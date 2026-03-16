// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.propertyBased

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.ConvertToScopeIntention
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFixBase
import org.jetbrains.kotlin.idea.refactoring.move.changePackage.ChangePackageIntention

class K1IntentionPolicy : KotlinIntentionPolicy() {
    override fun shouldCheckPreview(action: IntentionAction): Boolean {
        val unwrapped = IntentionActionDelegate.unwrap(action)
        val skipPreview =
          action.familyName == "Create from usage" || // Starts template but may also perform modifications before that; thus not so easy to support
          unwrapped is ConvertToScopeIntention || // Performs reference search which must be run under progress. Probably we can generate diff excluding references?..
          unwrapped is CreateCallableFromUsageFixBase<*> || // Performs too much of complex stuff. Not sure whether it should start in write action...
          unwrapped is ChangePackageIntention // Just starts the template; no reasonable preview could be displayed
        return !skipPreview
    }
}