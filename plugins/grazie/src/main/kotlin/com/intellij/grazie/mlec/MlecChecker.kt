package com.intellij.grazie.mlec

import ai.grazie.gec.model.problem.Problem
import ai.grazie.gec.model.problem.SentenceWithProblems
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.locale
import ai.grazie.text.exclusions.SentenceWithExclusions
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.rule.SentenceBatcher
import com.intellij.grazie.rule.SentenceTokenizer.Sentence
import com.intellij.grazie.rule.SentenceTokenizer.tokenize
import com.intellij.grazie.text.*
import com.intellij.grazie.utils.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.FileViewProvider
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

private val logger = LoggerFactory.getLogger(MlecChecker::class.java)

class MlecChecker : ExternalTextChecker() {
  private val incompleteSentenceMessages = setOf(
    "Missing verb", "Missing auxiliary verb", "Missing pronoun", "Incorrect subject-verb form", "Incorrect grammar",
    "Incorrect verb form", "Incorrect noun form", "Multiple mistakes", "Incorrect verb tense form"
  )

  override fun getRules(locale: Locale): List<Rule> {
    val targetLanguage = locale.language ?: return emptyList()
    val (_, rules) = Constants.mlecRules.entries.find { it.key.locale.language == targetLanguage } ?: return emptyList()
    return rules
  }

  override suspend fun checkExternally(content: TextContent): Collection<TextProblem> {
    if (!GrazieCloudConnector.seemsCloudConnected() || !NaturalTextDetector.seemsNatural(content.toString())) return emptyList()
    if (HighlightingUtil.skipExpensivePrecommitAnalysis(content.containingFile)) return emptyList()

    val stripPrefixLength = HighlightingUtil.stripPrefix(content)
    val detected = getLanguageIfAvailable(content.toString().substring(stripPrefixLength)) ?: return emptyList()
    val rules = Constants.mlecRules[detected] ?: return emptyList()
    if (rules.none { it.isCurrentlyEnabled(content) }) return emptyList()

    val typos = getTypos(detected, content, stripPrefixLength).takeIf { it.isNotEmpty() } ?: return emptyList()

    return typos
      .mapNotNull { typo ->
        val underline: TextRange = typo.highlighting.underline ?: return@mapNotNull null

        val fixes = typo.fixes.map { it.display() }
        val errorText = underline.substring(content.toString())

        val rule =
          if (detected == Language.ENGLISH && typo.info.id.id.endsWith("article.missing")) Constants.enMissingArticle
          else rules.last()
        if (!rule.isCurrentlyEnabled(content)) return@mapNotNull null

        object : GrazieProblem(typo, rule, content) {
          override fun getDescriptionTemplate(isOnTheFly: Boolean): @InspectionMessage String {
            val description = super.getDescriptionTemplate(isOnTheFly)
            if (ApplicationManager.getApplication().isUnitTestMode()) return "${rule.globalId}: $description"
            return description
          }

          override fun fitsGroup(group: RuleGroup): Boolean {
            if (RuleGroup.SENTENCE_START_CASE in group.rules && StringUtil.capitalize(errorText) in fixes) {
              val prev = content.subSequence(0, underline.startOffset).toString().trim()
              if (prev.isBlank() ||
                  Text.Latin.isEndPunctuation(prev) ||
                  Text.findParagraphRange(content, underline).startOffset == underline.startOffset) {
                return true
              }
            }

            if (RuleGroup.SENTENCE_END_PUNCTUATION in group.rules && "$errorText." in fixes) {
              return content.subSequence(underline.endOffset, content.length).isBlank() ||
                     Text.findParagraphRange(content, underline).endOffset == underline.endOffset
            }

            if (RuleGroup.INCOMPLETE_SENTENCE in group.rules && typo.message in incompleteSentenceMessages) {
              return true
            }

            return super.fitsGroup(group)
          }
        }
      }
  }

  private suspend fun getTypos(language: Language, text: TextContent, stripPrefixLength: Int): List<Problem> {
    val subText = text.subText(TextRange(stripPrefixLength, text.length)) ?: return emptyList()
    val sentences = tokenize(subText)
    val parsed: Map<SentenceWithExclusions, SentenceWithProblems?>? = runMlec(sentences, language, text.containingFile.viewProvider)
    if (parsed.isNullOrEmpty()) return emptyList()

    val result = ArrayList<Problem>()
    for (sentence in sentences) {
      val corrections = parsed[sentence.swe()]?.problems ?: continue
      val start = sentence.start + stripPrefixLength
      if (!text.hasUnknownFragmentsIn(TextRange.from(start, sentence.text.trimEnd().length))) {
        corrections.forEach {
          result.add(it.withOffset(start))
        }
      }
    }
    return result
  }

  private suspend fun runMlec(
    sentences: List<Sentence>, language: Language,
    vp: FileViewProvider,
  ): Map<SentenceWithExclusions, SentenceWithProblems?>? {
    val queries = sentences.map { it.swe() }
    if (!GrazieCloudConnector.seemsCloudConnected()) {
      return LinkedHashMap()
    }

    return try {
      val parser = service<ServerBatcherHolder>().get(language)?.forFile(vp)
      parser?.parseAsync(queries)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: RuntimeException) {
      logger.warn("Got exception from MLEC", e)
      LinkedHashMap()
    }
  }

  @Suppress("NonAsciiCharacters")
  object Constants {
    const val MLEC_RULE_PREFIX = "Grazie.MLEC"
    val enMissingArticle = mlecRule(
      "$MLEC_RULE_PREFIX.En.MissingArticle",
      Language.ENGLISH,
      presentableName = "Missing article check (ML)",
      category = "Machine Learning",
      description = "Missing article detection powered by Grazie machine learning"
    )
    val mlecRules = mapOf(
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
      }
  }

  @Service
  private class ServerBatcherHolder : LanguageHolder<SentenceBatcher<SentenceWithProblems>>() {
    private class ServerBatcher(
      language: Language,
    ) : SentenceBatcher<SentenceWithProblems>(language, 32), Disposable {
      override suspend fun parse(sentences: List<SentenceWithExclusions>, project: Project): Map<SentenceWithExclusions, SentenceWithProblems> {
        if (GrazieCloudConnector.EP_NAME.extensionList.any { it.isAfterRecentGecError() }) {
          return emptyMap()
        }
        return GrazieCloudConnector.EP_NAME.extensionList
                 .firstNotNullOfOrNull { it.mlec(sentences, language, project) }
                 ?.zip(sentences)
                 ?.associate { it.second to it.first }
               ?: emptyMap()
      }

      override fun dispose() {}

      init {
        GrazieConfig.subscribe(this) {
          clearCache()
        }
        GrazieCloudConnector.EP_NAME.forEachExtensionSafe { it.subscribeToAuthorizationStateEvents(this) { clearCache() } }
      }
    }

    init {
      update(
        listOf(ServerBatcher(Language.ENGLISH), ServerBatcher(Language.UKRAINIAN), ServerBatcher(Language.GERMAN)).associateBy { it.language }
      )
    }
  }

}
