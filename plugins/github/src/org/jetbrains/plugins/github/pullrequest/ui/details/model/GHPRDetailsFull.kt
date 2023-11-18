// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import java.util.*

data class GHPRDetailsFull(val id: GHPRIdentifier,
                           val url: String,
                           val author: GHActor,
                           val createdAt: Date,
                           val titleHtml: @NlsSafe String,
                           val description: String?,
                           val descriptionHtml: @NlsSafe String?,
                           val canEditDescription: Boolean)
