// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.expectactual

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.idea.base.psi.mustHaveValOrVar
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.expectActual.KotlinTypeInaccessibleException
import org.jetbrains.kotlin.idea.quickfix.TypeAccessibilityChecker
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateClassUtil.getTypeDescription
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHint
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

fun <D : KtNamedDeclaration> generateExpectOrActualInFile(
    project: Project,
    editor: Editor?,
    originalFile: KtFile,
    targetFile: KtFile,
    targetClass: KtClassOrObject?,
    element: D,
    module: Module,
    generateIt: KtPsiFactory.(Project, TypeAccessibilityChecker, D) -> D?
) {
    val factory = KtPsiFactory(project)
    val targetClassPointer = targetClass?.createSmartPointer()
    val targetFilePointer = targetFile.createSmartPointer()
    DumbService.getInstance(project).runWhenSmart(fun() {
        val generated = try {
            factory.generateIt(project, TypeAccessibilityChecker.create(project, module), element)
        } catch (e: KotlinTypeInaccessibleException) {
            if (editor != null) {
                showErrorHint(
                    project, editor,
                    escapeXml(KotlinBundle.message("fix.create.declaration.error", element.getTypeDescription(), e.message)),
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
                        if (oldPackageDirective.text.isEmpty()) {
                            resultTargetFile.addAfter(factory.createNewLine(2), resultTargetFile.importList ?: oldPackageDirective) //
                        }
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

                else -> {
                    resultTargetFile.add(factory.createNewLine(1))
                    resultTargetFile.add(generated) as KtElement
                }
            }

            ShortenReferences.DEFAULT.process(generatedDeclaration.reformatted() as KtElement)
        } ?: return

        EditorHelper.openInEditor(shortened)?.caretModel?.moveToOffset(
            (shortened as? KtNamedDeclaration)?.nameIdentifier?.startOffset ?: shortened.startOffset,
            true,
        )
    })
}

fun TypeAccessibilityChecker.isCorrectAndHaveAccessibleModifiers(declaration: KtNamedDeclaration, showErrorHint: Boolean = false): Boolean {
    if (declaration.anyInaccessibleModifier(INACCESSIBLE_MODIFIERS, showErrorHint)) return false

    if (declaration is KtFunction && declaration.hasBody() && declaration.containingClassOrObject?.isInterfaceClass() == true) {
        if (showErrorHint) showInaccessibleDeclarationError(
            declaration,
            KotlinBundle.message("the.function.declaration.shouldn.t.have.a.default.implementation")
        )
        return false
    }

    if (!showErrorHint) return checkAccessibility(declaration)

    val types = incorrectTypes(declaration).ifEmpty { return true }
    showInaccessibleDeclarationError(
        declaration,
        KotlinBundle.message(
            "some.types.are.not.accessible.from.0.1",
            targetModule.name,
            TypeAccessibilityChecker.typesToString(types)
        )
    )

    return false
}

private val INACCESSIBLE_MODIFIERS = listOf(KtTokens.PRIVATE_KEYWORD, KtTokens.CONST_KEYWORD, KtTokens.LATEINIT_KEYWORD)

private fun KtModifierListOwner.anyInaccessibleModifier(modifiers: Collection<KtModifierKeywordToken>, showErrorHint: Boolean): Boolean {
    for (modifier in modifiers) {
        if (hasModifier(modifier)) {
            if (showErrorHint) showInaccessibleDeclarationError(this, KotlinBundle.message("the.declaration.has.0.modifier", modifier))
            return true
        }
    }
    return false
}

fun showInaccessibleDeclarationError(element: PsiElement, message: String, editor: Editor? = element.findExistingEditor()) {
    editor?.let {
        showErrorHint(element.project, editor, escapeXml(message), KotlinBundle.message("inaccessible.declaration"))
    }
}

fun TypeAccessibilityChecker.Companion.typesToString(types: Collection<FqName?>, separator: CharSequence = "\n"): String {
    return types.toSet().joinToString(separator = separator) {
        it?.shortName()?.asString() ?: "<Unknown>"
    }
}

fun TypeAccessibilityChecker.findAndApplyExistingClasses(elements: Collection<KtNamedDeclaration>): Set<String> {
    var classes = elements.filterIsInstance<KtClassOrObject>()
    while (classes.isNotEmpty()) {
        val existingNames = classes.mapNotNull { it.fqName?.asString() }.toHashSet()
        existingTypeNames = existingNames

        val newExistingClasses = classes.filter { isCorrectAndHaveAccessibleModifiers(it) }
        if (classes.size == newExistingClasses.size) return existingNames

        classes = newExistingClasses
    }

    return existingTypeNames
}
