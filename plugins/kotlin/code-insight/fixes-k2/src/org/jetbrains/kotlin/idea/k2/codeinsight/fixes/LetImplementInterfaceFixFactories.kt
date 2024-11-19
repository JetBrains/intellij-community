// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.createSmartPointer
import com.intellij.util.application
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.utils.containsStarProjections
import org.jetbrains.kotlin.idea.codeinsight.utils.isInterface
import org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersHandler
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.isObjectLiteral
import org.jetbrains.kotlin.types.Variance

internal object LetImplementInterfaceFixFactories {

    val argumentTypeMismatchFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.expectedType, diagnostic.actualType)
        )
    }

    val initializerTypeMismatchFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.expectedType, diagnostic.actualType)
        )
    }

    val assignmentTypeMismatchFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.expectedType, diagnostic.actualType)
        )
    }

    val returnTypeMismatchFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.expectedType, diagnostic.actualType)
        )
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.createFixIfAvailable(
        expectedType: KaType,
        actualType: KaType,
    ): LetImplementInterfaceFix? {
        if (!expectedType.isInterface() ||
            expectedType.containsStarProjections() ||
            expectedType.expandedSymbol in actualType.allSupertypes.map { it.expandedSymbol }
        ) return null

        val expressionTypeDeclaration = actualType
            .expandedSymbol
            ?.takeIf { it.origin == KaSymbolOrigin.SOURCE }
            ?.psi as? KtClassOrObject

        val actualTypeNotNullable = actualType.withNullability(KaTypeNullability.NON_NULLABLE)
        if (expressionTypeDeclaration == null || expectedType.semanticallyEquals(actualTypeNotNullable)) return null

        val elementContext = buildElementContext(
            expressionTypeDeclaration,
            expectedType,
            actualType
        )

        return LetImplementInterfaceFix(expressionTypeDeclaration, elementContext)
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.buildElementContext(
        element: KtClassOrObject,
        expectedType: KaType,
        actualType: KaType,
    ): ElementContext {
        val typeDescription = if (element.isObjectLiteral())
            KotlinBundle.message("the.anonymous.object")
        else
            "'${renderShort(actualType)}'"

        val expectedTypeNotNullable = expectedType.withNullability(KaTypeNullability.NON_NULLABLE)

        val expectedTypeName = renderShort(expectedTypeNotNullable)

        val expectedTypeNameSourceCode = expectedTypeNotNullable.render(
            renderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES,
            position = Variance.INVARIANT
        )

        val verb = if (actualType.isInterface()) KotlinBundle.message("text.extend") else KotlinBundle.message("text.implement")

        return ElementContext(
            expectedTypeName,
            expectedTypeNameSourceCode,
            prefix = KotlinBundle.message("let.0.1", typeDescription, verb),
        )
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.renderShort(type: KaType): String = type.render(
        renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
        position = Variance.INVARIANT
    )

    private data class ElementContext(
        val expectedTypeName: String,
        val expectedTypeNameSourceCode: String,
        val prefix: String,
    )

    private class LetImplementInterfaceFix(
        element: KtClassOrObject,
        private val elementContext: ElementContext,
    ) : KotlinQuickFixAction<KtClassOrObject>(element), LowPriorityAction {

        override fun getFamilyName(): String = KotlinBundle.message("let.type.implement.interface")
        override fun getText(): String = KotlinBundle.message("0.interface.1", elementContext.prefix, elementContext.expectedTypeName)
        override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = true
        override fun startInWriteAction(): Boolean = false

        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val element = element ?: return
            val point = element.createSmartPointer()

            val superTypeEntry = KtPsiFactory(project).createSuperTypeEntry(elementContext.expectedTypeNameSourceCode)
            application.runWriteAction {
                val entryElement = element.addSuperTypeListEntry(superTypeEntry)
                ShortenReferencesFacility.getInstance().shorten(entryElement)
            }

            val newElement = point.element ?: return
            val implementMembersHandler = KtImplementMembersHandler()
            if (implementMembersHandler.collectMembersToGenerateUnderProgress(newElement).isEmpty()) return

            if (editor != null) {
                editor.caretModel.moveToOffset(element.textRange.startOffset)
                val containingFile = element.containingFile
                FileEditorManager.getInstance(project).openFile(containingFile.virtualFile, /* focusEditor = */ true)
                implementMembersHandler.invoke(project, editor, containingFile)
            }
        }
    }
}
