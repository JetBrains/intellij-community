package com.intellij.grazie.config

import com.intellij.util.xmlb.annotations.Property

data class CheckingContext(
  @Property val isCheckInCommitMessagesEnabled: Boolean = true,
  @Property val isCheckInStringLiteralsEnabled: Boolean = false,
  @Property val isCheckInCommentsEnabled: Boolean = true,
  @Property val isCheckInDocumentationEnabled: Boolean = true,

  /** The IDs of the programming languages where the grammar checking is explicitly disabled */
  @Property val disabledLanguages: Set<String> = HashSet()
)
