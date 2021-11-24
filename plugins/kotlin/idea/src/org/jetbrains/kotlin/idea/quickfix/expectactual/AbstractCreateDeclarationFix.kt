// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.expectactual

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.SlowOperations
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.quickfix.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.TypeAccessibilityChecker
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHint
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.reformatted
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.startOffset

abstract class AbstractCreateDeclarationFix<D : KtNamedDeclaration>(
    declaration: D,
    protected val module: Module,
    protected val generateIt: KtPsiFactory.(Project, TypeAccessibilityChecker, D) -> D?
) : KotlinQuickFixAction<D>(declaration) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.create.expect.actual")

    protected val elementType: String = element.getTypeDescription()

    override fun startInWriteAction() = false

    protected abstract fun findExistingFileToCreateDeclaration(
        originalFile: KtFile,
        originalDeclaration: KtNamedDeclaration
    ): KtFile?

    protected fun getOrCreateImplementationFile(): KtFile? {
        val declaration = element as? KtNamedDeclaration ?: return null
        val parent = declaration.parent
        return (parent as? KtFile)?.let { findExistingFileToCreateDeclaration(it, declaration) }
            ?: createFileForDeclaration(module, declaration)
    }

    protected fun doGenerate(
        project: Project,
        editor: Editor?,
        originalFile: KtFile,
        targetFile: KtFile,
        targetClass: KtClassOrObject?
    ) {
        val factory = KtPsiFactory(project)
        val targetClassPointer = targetClass?.createSmartPointer()
        val targetFilePointer = targetFile.createSmartPointer()
        DumbService.getInstance(project).runWhenSmart(fun() {
            val generated = try {
                element?.let {
                    SlowOperations.allowSlowOperations(ThrowableComputable {
                        factory.generateIt(project, TypeAccessibilityChecker.create(project, module), it)
                    })
                }
            } catch (e: KotlinTypeInaccessibleException) {
                if (editor != null) {
                    showErrorHint(
                        project, editor,
                        escapeXml(KotlinBundle.message("fix.create.declaration.error", elementType, e.message)),
                        KotlinBundle.message("fix.create.declaration.error.inaccessible.type")
                    )
                }
                null
            } ?: return

            val shortened = project.executeWriteCommand(KotlinBundle.message("fix.create.expect.actual"), null) {
                val resultTargetFile = targetFilePointer.element ?: return@executeWriteCommand null
                if (resultTargetFile.packageDirective?.fqName != originalFile.packageDirective?.fqName &&
                    resultTargetFile.declarations.isEmpty()
                ) {
                    val packageDirective = originalFile.packageDirective
                    if (packageDirective != null) {
                        val oldPackageDirective = resultTargetFile.packageDirective
                        val newPackageDirective = packageDirective.copy() as KtPackageDirective
                        if (oldPackageDirective != null) {
                            if (oldPackageDirective.text.isEmpty()) resultTargetFile.addAfter(factory.createNewLine(2), oldPackageDirective)
                            oldPackageDirective.replace(newPackageDirective)
                        } else {
                            resultTargetFile.add(newPackageDirective)
                        }
                    }
                }

                val resultTargetClass = targetClassPointer?.element
                val generatedDeclaration = when {
                    resultTargetClass != null -> {
                        if (generated is KtPrimaryConstructor && resultTargetClass is KtClass)
                            resultTargetClass.createPrimaryConstructorIfAbsent().replace(generated)
                        else
                            resultTargetClass.addDeclaration(generated as KtNamedDeclaration)
                    }
                    else -> resultTargetFile.add(generated) as KtElement
                }

                ShortenReferences.DEFAULT.process(generatedDeclaration.reformatted() as KtElement)
            } ?: return

            EditorHelper.openInEditor(shortened)?.caretModel?.moveToOffset(
                (shortened as? KtNamedDeclaration)?.nameIdentifier?.startOffset ?: shortened.startOffset,
                true,
            )
        })
    }
}