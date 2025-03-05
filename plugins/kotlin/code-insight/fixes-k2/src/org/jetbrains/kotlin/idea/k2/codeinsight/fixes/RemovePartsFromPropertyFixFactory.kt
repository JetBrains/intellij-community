// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.RemovePartsFromPropertyUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object RemovePartsFromPropertyFixFactory {

    val abstractPropertyWithGetter = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AbstractPropertyWithGetter ->
        createQuickFix(diagnostic.psi)
    }

    val abstractPropertyWithInitializer = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AbstractPropertyWithInitializer ->
        createQuickFix(diagnostic.psi)
    }

    val abstractPropertyWithSetter = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AbstractPropertyWithSetter ->
        createQuickFix(diagnostic.psi)
    }

    val inapplicableLateinitModifier = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InapplicableLateinitModifier ->
        createQuickFix(diagnostic.psi)
    }

    val propertyInitializerInInterface = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.PropertyInitializerInInterface ->
        createQuickFix(diagnostic.psi)
    }

    private fun KaSession.createQuickFix(
        element: KtElement,
    ): List<RemovePartsFromPropertyFix> {
        val property = element.getParentOfType<KtProperty>(strict = false) ?: return emptyList()

        val removeInitializer = property.hasInitializer()
        val removeGetter = property.getter?.bodyExpression != null
        val removeSetter = property.setter?.bodyExpression != null

        if (!removeInitializer && !removeGetter && !removeSetter) return emptyList()

        val elementContext = ElementContext(
            removeInitializer,
            removeGetter,
            removeSetter,
            getTypeInfo(property),
        )

        return listOf(
            RemovePartsFromPropertyFix(property, elementContext)
        )
    }

    private fun KaSession.getTypeInfo(property: KtProperty): CallableReturnTypeUpdaterUtils.TypeInfo? {
        if (property.hasInitializer() && property.initializer != null && property.typeReference == null) {
            return CallableReturnTypeUpdaterUtils.getTypeInfo(property)
        }
        return null
    }

    private data class ElementContext(
        val removeInitializer: Boolean,
        val removeGetter: Boolean,
        val removeSetter: Boolean,
        val typeInfo: CallableReturnTypeUpdaterUtils.TypeInfo?,
    )

    private class RemovePartsFromPropertyFix(
        element: KtProperty,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtProperty, ElementContext>(element, elementContext) {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.parts.from.property")

        override fun getPresentation(
            context: ActionContext,
            element: KtProperty,
        ): Presentation {
            val elementContext = getElementContext(context, element)
            val actionName = RemovePartsFromPropertyUtils.getRemovePartsFromPropertyActionName(
                removeInitializer = elementContext.removeInitializer,
                removeGetter = elementContext.removeGetter,
                removeSetter = elementContext.removeSetter,
            )
            return Presentation.of(actionName)
        }

        override fun invoke(
            actionContext: ActionContext,
            element: KtProperty,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            if (elementContext.removeGetter) {
                element.getter?.delete()
            }

            if (elementContext.removeSetter) {
                element.setter?.delete()
            }

            if (elementContext.removeInitializer) {
                removeInitializer(actionContext.project, element, elementContext, updater)
            }
        }

        private fun removeInitializer(
            project: Project,
            element: KtProperty,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val initializer = element.initializer ?: return
            element.deleteChildRange(element.equalsToken ?: initializer, initializer)
            if (elementContext.typeInfo == null) return

            CallableReturnTypeUpdaterUtils.updateType(
                declaration = element,
                typeInfo = elementContext.typeInfo,
                project = project,
                updater = updater,
            )
        }
    }
}
