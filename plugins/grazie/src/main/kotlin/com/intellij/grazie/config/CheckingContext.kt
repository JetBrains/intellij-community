package com.intellij.grazie.config

import com.intellij.util.xmlb.annotations.Property

data class CheckingContext(@Property val isCheckInCommitMessagesEnabled: Boolean = false,
                           @Property val isCheckInStringLiteralsEnabled: Boolean = false,
                           @Property val isCheckInCommentsEnabled: Boolean = true,
                           @Property val isCheckInDocumentationEnabled: Boolean = true)
