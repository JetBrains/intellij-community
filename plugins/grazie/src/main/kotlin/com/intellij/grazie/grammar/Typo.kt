// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.*
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.languagetool.rules.IncorrectExample
import org.languagetool.rules.Rule
import org.languagetool.rules.RuleMatch
import org.slf4j.LoggerFactory

data class Typo(val location: Location, val info: Info, val fixes: LinkedSet<String> = LinkedSet()) {
  companion object {
    private val logger = LoggerFactory.getLogger(Typo::class.java)
  }

  data class Location(val errorRange: IntRange, val patternRange: IntRange, val textRanges: Collection<IntRange> = emptyList(),
                      val pointer: PsiPointer<PsiElement>? = null) {
    val element: PsiElement?
      get() = pointer?.element

    val errorText = try {
      pointer?.element?.text?.subSequence(errorRange)?.toString()
    }
    catch (t: Throwable) {
      logger.warn("Got an exception during getting typo word:\n${pointer?.element!!.text}")
      throw t
    }

    val patternText = try {
      pointer?.element?.text?.subSequence(patternRange)?.toString()
    }
    catch (t: Throwable) {
      logger.warn("Got an exception during getting pattern text:\n${pointer?.element!!.text}")
      throw t
    }
  }

  data class Info(val lang: Lang, val rule: Rule, private val myShortMessage: String, val message: String) {
    val shortMessage: String by lazy {
      myShortMessage.trimToNull() ?: rule.description.trimToNull() ?: rule.category.name
    }

    val incorrectExample: IncorrectExample? by lazy {
      val withCorrections = rule.incorrectExamples.filter { it.corrections.isNotEmpty() }.takeIf { it.isNotEmpty() }
      (withCorrections ?: rule.incorrectExamples).minBy { it.example.length }
    }
  }

  /** Constructor for LangTool, applies fixes to RuleMatch (Main constructor doesn't apply fixes) */
  constructor(match: RuleMatch, lang: Lang, offset: Int = 0) : this(
    Location(match.toIntRange(offset), IntRange(match.patternFromPos, match.patternToPos - 1).withOffset(offset)),
    Info(lang, match.rule, match.shortMessage, match.messageSanitized),
    LinkedSet(match.getSuggestedReplacements())
  )

  val category: Category?
    @Deprecated("Use RuleGroup instead")
    @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
    get() {
      val category = info.rule.category.name
      return Category.values().find { it.name == category }
    }

  /**
   * A grammar typo category
   *
   * All typos have categories that can be found in the Grazie plugin UI tree in settings/preferences.
   */
  @Deprecated("Use RuleGroup instead")
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  enum class Category {
    /** General categories  */

    TEXT_ANALYSIS,

    /** Rules about detecting uppercase words where lowercase is required and vice versa.  */
    CASING,

    /** Rules about spelling terms as one word or as separate words.  */
    COMPOUNDING,

    GRAMMAR,

    /** Spelling issues.  */
    TYPOS,

    PUNCTUATION,

    LOGIC,

    /** Problems like incorrectly used dash or quote characters.  */
    TYPOGRAPHY,

    /** Words that are easily confused, like 'there' and 'their' in English.  */
    CONFUSED_WORDS,

    REPETITIONS,

    REDUNDANCY,

    /** General style issues not covered by other categories, like overly verbose wording.  */
    STYLE,

    GENDER_NEUTRALITY,

    /** Logic, content, and consistency problems.  */
    SEMANTICS,

    /** Colloquial style.  */
    COLLOQUIALISMS,

    /** Regionalisms: words used only in another language variant or used with different meanings.  */
    REGIONALISMS,

    /** False friends: words easily confused by language learners because a similar word exists in their native language.  */
    FALSE_FRIENDS,

    /** Rules that only make sense when editing Wikipedia (typically turned off by default in LanguageTool).  */
    WIKIPEDIA,

    /** Miscellaneous rules that don't fit elsewhere.  */
    MISC,


    /** English categories */

    AMERICAN_ENGLISH,
    AMERICAN_ENGLISH_STYLE,
    BRITISH_ENGLISH,
    BRE_STYLE_OXFORD_SPELLING,
    CREATIVE_WRITING,
    MISUSED_TERMS_EU_PUBLICATIONS,
    NONSTANDARD_PHRASES,
    PLAIN_ENGLISH;
  }

}
