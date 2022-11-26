// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.containsStarProjections
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.isObjectLiteral
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.isInterface

class LetImplementInterfaceFix(
    element: KtClassOrObject,
    expectedType: KotlinType,
    expressionType: KotlinType
) : KotlinQuickFixAction<KtClassOrObject>(element), LowPriorityAction {

    private fun KotlinType.renderShort() = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(this)

    private val expectedTypeName: String

    private val expectedTypeNameSourceCode: String

    private val prefix: String

    private val validExpectedType = with(expectedType) {
        isInterface() &&
                !containsStarProjections() &&
                constructor !in TypeUtils.getAllSupertypes(expressionType).map(KotlinType::constructor)
    }

    init {
        val expectedTypeNotNullable = TypeUtils.makeNotNullable(expectedType)
        expectedTypeName = expectedTypeNotNullable.renderShort()
        expectedTypeNameSourceCode = IdeDescriptorRenderers.SOURCE_CODE.renderType(expectedTypeNotNullable)

        val verb = if (expressionType.isInterface()) KotlinBundle.message("text.extend") else KotlinBundle.message("text.implement")
        val typeDescription = if (element.isObjectLiteral())
            KotlinBundle.message("the.anonymous.object")
        else
            "'${expressionType.renderShort()}'"
        prefix = KotlinBundle.message("let.0.1", typeDescription, verb)

    }

    override fun getFamilyName() = KotlinBundle.message("let.type.implement.interface")
    override fun getText() = KotlinBundle.message("0.interface.1", prefix, expectedTypeName)

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile) = validExpectedType

    override fun startInWriteAction() = false

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val point = element.createSmartPointer()

        val superTypeEntry = KtPsiFactory(project).createSuperTypeEntry(expectedTypeNameSourceCode)
        runWriteAction {
            val entryElement = element.addSuperTypeListEntry(superTypeEntry)
            ShortenReferences.DEFAULT.process(entryElement)
        }

        val newElement = point.element ?: return
        val implementMembersHandler = ImplementMembersHandler()
        if (implementMembersHandler.collectMembersToGenerate(newElement).isEmpty()) return

        if (editor != null) {
            editor.caretModel.moveToOffset(element.textRange.startOffset)
            val containingFile = element.containingFile
            FileEditorManager.getInstance(project).openFile(containingFile.virtualFile, true)
            implementMembersHandler.invoke(project, editor, containingFile)
        }
    }
}
