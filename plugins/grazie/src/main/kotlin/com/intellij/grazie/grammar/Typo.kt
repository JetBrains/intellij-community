// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import org.jetbrains.annotations.ApiStatus

@Deprecated("Use TextProblem instead")
class Typo {
  /**
   * A grammar typo category
   *
   * All typos have categories that can be found in the Grazie plugin UI tree in settings/preferences.
   */
  @Deprecated("Use RuleGroup instead")
  @ApiStatus.ScheduledForRemoval
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
