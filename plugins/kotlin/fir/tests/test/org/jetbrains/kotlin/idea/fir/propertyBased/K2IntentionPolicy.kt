// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.propertyBased

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import org.jetbrains.kotlin.idea.propertyBased.KotlinIntentionPolicy

internal class K2IntentionPolicy : KotlinIntentionPolicy() {
    override fun shouldCheckPreview(action: IntentionAction): Boolean {
        val unwrapped = IntentionActionDelegate.unwrap(action)
        val skipPreview =
          action.familyName == "Create from usage" || // Starts template but may also perform modifications before that; thus not so easy to support
          unwrapped.javaClass.name in skipPreviewIntentionClassNames
        return !skipPreview
    }

    private val skipPreviewIntentionClassNames =
        setOf(
            "org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ChangePackageIntention", // Just starts the template; no reasonable preview could be displayed
            "org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix",
            "org.jetbrains.kotlin.idea.quickfix.K2EnableUnsupportedFeatureFix"
        )
}
