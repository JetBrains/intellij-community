// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.modcommand.ModCommandAction

interface CleanupModCommandFix : ModCommandAction {
    override fun asIntention(): IntentionAction = object : IntentionWrapper(super.asIntention()), CleanupFix { }
}