// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.trialPromotion.vision

import kotlinx.serialization.Serializable

// TODO: deduplicate with com.intellij.platform.whatsNew.WhatsNewInVisionContentProvider.Container
@Serializable
internal class Container(val entities: List<Page>)

@Serializable
internal data class Page(val id: Int,
                         val publicVars: List<PublicVar>,
                         val actions: List<Action>,
                         val languages: List<Language>,
                         val html: String)

@Serializable
internal data class Action(val value: String, val description: String)

@Serializable
internal data class Language(val code: String)

@Serializable
internal data class PublicVar(val value: String, val description: String)
