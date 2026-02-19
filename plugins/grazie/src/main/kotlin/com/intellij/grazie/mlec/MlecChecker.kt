package com.intellij.grazie.mlec

import ai.grazie.gec.model.problem.Problem
import ai.grazie.gec.model.problem.ProblemFix
import ai.grazie.gec.model.problem.SentenceWithProblems
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.locale
import ai.grazie.text.exclusions.SentenceWithExclusions
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.cloud.APIQueries
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.rule.SentenceBatcher
import com.intellij.grazie.text.ExternalTextChecker
import com.intellij.grazie.text.GrazieProblem
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.RuleGroup
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.Text
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.grazie.utils.getAssociatedGrazieRule
import com.intellij.grazie.utils.getProblems
import com.intellij.grazie.utils.isEnabledInState
import com.intellij.grazie.utils.shouldCheckGrammarStyle
import com.intellij.grazie.utils.underline
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import java.util.Locale
import kotlin.String

class MlecChecker : ExternalTextChecker() {
  override fun getRules(locale: Locale): List<Rule> {
    val targetLanguage = locale.language ?: return emptyList()
    val (_, rules) = Constants.mlecRules.entries.find { it.key.locale.language == targetLanguage } ?: return emptyList()
    return rules
  }

  override suspend fun checkExternally(context: ProofreadingContext): Collection<TextProblem> {
    if (!GrazieCloudConnector.seemsCloudConnected() || !context.shouldCheckGrammarStyle()) return emptyList()
    val rules = Constants.mlecRules[context.language] ?: return emptyList()
    if (rules.none { it.isCurrentlyEnabled(context.text) }) return emptyList()

    val typos = getProblems(context, MlecServerBatcherHolder::class.java)?.takeIf { it.isNotEmpty() } ?: return emptyList()
    return typos
      .mapNotNull { typo ->
        val underline: TextRange = typo.highlighting.underline ?: return@mapNotNull null
        val rule =
          if (context.language == Language.ENGLISH && typo.info.id.id.endsWith("article.missing")) Constants.enMissingArticle
          else rules.last()
        if (!rule.isCurrentlyEnabled(context.text)) return@mapNotNull null
        MlecProblem(typo, rule, context.text, underline)
      }
      .filterNot { isKnownMlecBug(it) }
  }

  private fun isKnownMlecBug(problem: GrazieProblem): Boolean {
    if (problem.source.info.id.id == "mlec.en.grammar.article.incorrect") {
      return problem.source.fixes.any { fix ->
        val parts = fix.parts
          .filterIsInstance<ProblemFix.Part.Change>()
          .filter { part -> part.type == ProblemFix.Part.Change.ChangeType.REPLACE }
        if (parts.isEmpty()) return@any false
        parts.any { it.text.equals("an url", ignoreCase = true) }
      }
    }
    return false
  }

  @Suppress("NonAsciiCharacters")
  object Constants {
    const val MLEC_RULE_PREFIX: String = "Grazie.MLEC"
    val enMissingArticle: Rule = mlecRule(
      "$MLEC_RULE_PREFIX.En.MissingArticle",
      Language.ENGLISH,
      presentableName = "Missing article check (ML)",
      category = "Machine Learning",
      description = "Missing article detection powered by Grazie machine learning"
    )
    val mlecRules: Map<Language, List<Rule>> = mapOf(
      Language.ENGLISH to listOf(
        enMissingArticle,
        mlecRule(
          "${MLEC_RULE_PREFIX}.En.All",
          Language.ENGLISH,
          presentableName = "Grammar error correction (ML)",
          category = "Machine Learning",
          description = "Grammar error correction powered by Grazie machine learning"
        )
      ),
      Language.UKRAINIAN to listOf(
        mlecRule(
          "$MLEC_RULE_PREFIX.Uk.All",
          Language.UKRAINIAN,
          presentableName = "Граматичні помилки (ML)",
          category = "Машинне навчання",
          description = "Виправлення граматичних помилок методами машинного навчання Grazie"
        )
      ),
      Language.GERMAN to listOf(
        mlecRule(
          "$MLEC_RULE_PREFIX.De.All",
          Language.GERMAN,
          presentableName = "Grammatische Fehler (ML)",
          category = "Maschinelles Lernen",
          description = "Grammatikfehlerkorrigierung mithilfe von Methoden des maschinellen Lernens von Grazie"
        )
      )
    )

    private fun mlecRule(id: String, language: Language, presentableName: String, category: String, description: String): Rule =
      object : Rule(id, language, presentableName, category) {
        override fun getDescription(): String = description
        override fun isEnabledByDefault(domain: TextStyleDomain): Boolean {
          if (this.globalId != enMissingArticle.globalId) return super.isEnabledByDefault(domain)
          return getAssociatedGrazieRule(this)!!.isEnabledInState(GrazieConfig.get(), domain)
        }
      }
  }

  @Service
  private class MlecServerBatcherHolder : LanguageHolder<SentenceBatcher<SentenceWithProblems>>() {
    private class ServerBatcher(
      language: Language,
    ) : SentenceBatcher<SentenceWithProblems>(language, 32), Disposable {
      override suspend fun parse(sentences: List<SentenceWithExclusions>, project: Project): Map<SentenceWithExclusions, SentenceWithProblems> {
        if (GrazieCloudConnector.isAfterRecentGecError()) {
          return emptyMap()
        }
        return APIQueries.mlec(sentences, language, project)
                 ?.zip(sentences)
                 ?.associate { it.second to it.first }
               ?: emptyMap()
      }

      override fun dispose() {}

      init {
        GrazieConfig.subscribe(this) {
          clearCache()
        }
        GrazieCloudConnector.subscribeToAuthorizationStateEvents(this) { clearCache() }
      }
    }

    init {
      update(
        listOf(ServerBatcher(Language.ENGLISH), ServerBatcher(Language.UKRAINIAN), ServerBatcher(Language.GERMAN)).associateBy { it.language }
      )
    }
  }

  class MlecProblem(source: Problem, rule: Rule, text: TextContent, private val underline: TextRange) : GrazieProblem(source, rule, text) {
    companion object {
      private val incompleteSentenceMessages = setOf(
        "Missing verb", "Missing auxiliary verb", "Missing pronoun", "Incorrect subject-verb form", "Incorrect grammar",
        "Incorrect verb form", "Incorrect noun form", "Multiple mistakes", "Incorrect verb tense form"
      )
    }

    override fun getDescriptionTemplate(isOnTheFly: Boolean): @InspectionMessage String {
      val description = super.getDescriptionTemplate(isOnTheFly)
      if (ApplicationManager.getApplication().isUnitTestMode()) return "${rule.globalId}: $description"
      return description
    }

    override fun fitsGroup(group: RuleGroup): Boolean {
      val fixes = source.fixes.map { it.display() }
      val errorText = errorText
      if (RuleGroup.SENTENCE_START_CASE in group.rules && StringUtil.capitalize(errorText) in fixes) {
        val prev = text.subSequence(0, underline.startOffset).toString().trim()
        if (prev.isBlank() ||
            Text.Latin.isEndPunctuation(prev) ||
            Text.findParagraphRange(text, underline).startOffset == underline.startOffset) {
          return true
        }
      }

      if (RuleGroup.SENTENCE_END_PUNCTUATION in group.rules && "$errorText." in fixes) {
        return text.subSequence(underline.endOffset, text.length).isBlank() ||
               Text.findParagraphRange(text, underline).endOffset == underline.endOffset
      }

      if (RuleGroup.INCOMPLETE_SENTENCE in group.rules && source.message in incompleteSentenceMessages) {
        return true
      }

      return super.fitsGroup(group)
    }

    val errorText: String get() = underline.substring(text.toString())
  }
}
