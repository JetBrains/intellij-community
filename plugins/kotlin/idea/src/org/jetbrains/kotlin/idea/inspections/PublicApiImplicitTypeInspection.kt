// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.resolve.checkers.ExplicitApiDeclarationChecker

class PublicApiImplicitTypeInspection(
    @JvmField var reportInternal: Boolean = false,
    @JvmField var reportPrivate: Boolean = false
) : AbstractImplicitTypeInspection(
    { element, inspection ->
        // To avoid reporting public declarations multiple times (by IDE inspection and by compiler diagnostics),
        // we want to report them only when Explicit API is disabled in the compiler.
        val shouldCheckForPublic = element.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) == ExplicitApiMode.DISABLED
        val callableMemberDescriptor = element.resolveToDescriptorIfAny() as? CallableMemberDescriptor
        val forInternal = (inspection as PublicApiImplicitTypeInspection).reportInternal
        val forPrivate = inspection.reportPrivate
        ExplicitApiDeclarationChecker.returnTypeRequired(element, callableMemberDescriptor, shouldCheckForPublic, forInternal, forPrivate)
    }
) {

    override val problemText: String
        get() {
            return if (!reportInternal && !reportPrivate)
                KotlinBundle.message("for.api.stability.it.s.recommended.to.specify.explicitly.public.protected.declaration.types")
            else
                KotlinBundle.message("for.api.stability.it.s.recommended.to.specify.explicitly.declaration.types")
        }

  override fun getOptionsPane() = pane(
    checkbox("reportInternal", KotlinBundle.message("apply.also.to.internal.members")),
    checkbox("reportPrivate", KotlinBundle.message("apply.also.to.private.members")))
}