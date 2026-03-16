// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.psi.KtElement

/**
 * A factory to instantiate [IntentionAction]s for adding imports for unresolved references in Kotlin files.
 *
 * **Why is this required**: We cannot simply provide [AddImportModCommandAction] from [ImportQuickFix.asModCommandAction],
 * because IntelliJ IDEA Platform would prefer the mod command implementation over the original, changing the UX
 * for the IntelliJ IDEA users. So we use this factory instead to provide the desired actions for different environments.
 *
 * Currently, this factory has two implementations:
 *
 * - [Classic] to be used in IntelliJ IDEA.
 *    - Produces [ImportQuickFix]es with classic IntelliJ IDEA UX of choosing the desired import in a popup, and also with auto-import hints.
 * - [ModCommandBased] for Kotlin LSP Server scenario.
 *    - Produces more basic [AddImportModCommandAction]s with [com.intellij.modcommand.ModChooseAction] UX to select the desired import.
 *
 * The desired implementation should be registered as a service in the XML.
 *
 * Note: This factory is only responsible for constructing instances of import fixes.
 * See [org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories.AbstractImportQuickFixFactory]
 * for the logic which actually gathers the import candidates to construct the fixes.
 */
@ApiStatus.Internal
sealed interface KotlinAddImportActionFactory {
    fun createAddImportFix(
        element: KtElement,
        intentionText: @IntentionName String,
        importVariants: List<AutoImportVariant>,
    ): IntentionAction

    class Classic : KotlinAddImportActionFactory {
        override fun createAddImportFix(
            element: KtElement,
            intentionText: @IntentionName String,
            importVariants: List<AutoImportVariant>,
        ): IntentionAction {
            return ImportQuickFix(element, intentionText, importVariants)
        }
    }

    class ModCommandBased : KotlinAddImportActionFactory {
        override fun createAddImportFix(
            element: KtElement,
            intentionText: @IntentionName String,
            importVariants: List<AutoImportVariant>,
        ): IntentionAction {
            return AddImportModCommandAction(element, intentionText, importVariants).asIntention()
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): KotlinAddImportActionFactory = service<KotlinAddImportActionFactory>()
    }
}