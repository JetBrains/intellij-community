// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.SmartList
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.getReturnTypeReference
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.TemplateKind
import org.jetbrains.kotlin.idea.core.getFunctionBodyTextFromTemplate
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.liftToExpect
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.withExpectedActuals
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.utils.addIfNotNull

enum class BodySelectionType {
    NOTHING, FUNCTION, SETTER, GETTER
}

fun convertMemberToExtensionAndPrepareBodySelection(
    element: KtCallableDeclaration,
    allowExpected: Boolean = true,
    addImport: (KtFile, FqName) -> KtImportDirective,
): Pair<KtCallableDeclaration, BodySelectionType> {
    val expectedDeclaration = liftToExpect(element) as? KtCallableDeclaration
    if (expectedDeclaration != null) {
        withExpectedActuals(element).filterIsInstance<KtCallableDeclaration>().forEach {
            if (it != element) {
                processSingleDeclaration(it, allowExpected, addImport)
            }
        }
    }

    val classVisibility = element.containingClass()?.visibilityModifierType()
    val (extension, bodyTypeToSelect) = processSingleDeclaration(element, allowExpected, addImport)
    if (classVisibility != null && extension.visibilityModifier() == null) {
        runWriteAction {
            extension.addModifier(classVisibility)
        }
    }

    return extension to bodyTypeToSelect
}

private fun processSingleDeclaration(
    element: KtCallableDeclaration,
    allowExpected: Boolean,
    addImport: (KtFile, FqName) -> KtImportDirective,
): Pair<KtCallableDeclaration, BodySelectionType> {
    val containingClass = element.containingClassOrObject

    val isEffectivelyExpected = allowExpected && element.isExpectDeclaration()

    val file = element.containingKtFile
    val project = file.project
    val outermostParent = KtPsiUtil.getOutermostParent(element, file, false)

    val ktFilesToAddImports = LinkedHashSet<KtFile>()
    val javaCallsToFix = SmartList<PsiMethodCallExpression>()
    runWithModalProgressBlocking(project, KotlinBundle.message("searching.for.0", element.name!!)) {
        readAction {
            for (ref in ReferencesSearch.search(element)) {
                when (ref) {
                    is KtReference -> {
                        val refFile = ref.element.containingKtFile
                        if (refFile != file) {
                            ktFilesToAddImports.add(refFile)
                        }
                    }

                    is PsiReferenceExpression -> javaCallsToFix.addIfNotNull(ref.parent as? PsiMethodCallExpression)
                }
            }
        }
    }

    val typeParameterList = newTypeParameterList(element)

    val psiFactory = KtPsiFactory(project)

    val (extension, bodyTypeToSelect) = runWriteAction {
        val extension = file.addAfter(element, outermostParent) as KtCallableDeclaration
        file.addAfter(psiFactory.createNewLine(), outermostParent)
        file.addAfter(psiFactory.createNewLine(), outermostParent)
        element.delete()

        extension.setReceiverType(containingClass!!)

        if (typeParameterList != null) {
            if (extension.typeParameterList != null) {
                extension.typeParameterList!!.replace(typeParameterList)
            } else {
                extension.addBefore(typeParameterList, extension.receiverTypeReference)
                extension.addBefore(psiFactory.createWhiteSpace(), extension.receiverTypeReference)
            }
        }

        extension.modifierList?.getModifier(KtTokens.PROTECTED_KEYWORD)?.delete()
        extension.modifierList?.getModifier(KtTokens.ABSTRACT_KEYWORD)?.delete()
        extension.modifierList?.getModifier(KtTokens.OPEN_KEYWORD)?.delete()
        extension.modifierList?.getModifier(KtTokens.FINAL_KEYWORD)?.delete()

        if (isEffectivelyExpected && !extension.hasExpectModifier()) {
            extension.addModifier(KtTokens.EXPECT_KEYWORD)
        }

        var bodyTypeToSelect = BodySelectionType.NOTHING

        val bodyText = getFunctionBodyTextFromTemplate(
            project,
            if (extension is KtFunction) TemplateKind.FUNCTION else TemplateKind.PROPERTY_INITIALIZER,
            extension.name,
            extension.getReturnTypeReference()?.text ?: "Unit",
            extension.containingClassOrObject?.fqName
        )

        when (extension) {
            is KtFunction -> {
                if (!extension.hasBody() && !isEffectivelyExpected) { //TODO: methods in PSI for setBody
                    extension.add(psiFactory.createBlock(bodyText))
                    bodyTypeToSelect = BodySelectionType.FUNCTION
                }
            }

            is KtProperty -> {
                val templateProperty =
                    psiFactory.createDeclaration<KtProperty>("var v: Any\nget()=$bodyText\nset(value){\n$bodyText\n}")

                if (!isEffectivelyExpected) {
                    val templateGetter = templateProperty.getter!!
                    val templateSetter = templateProperty.setter!!

                    var getter = extension.getter
                    if (getter == null) {
                        getter = extension.addAfter(templateGetter, extension.typeReference) as KtPropertyAccessor
                        extension.addBefore(psiFactory.createNewLine(), getter)
                        bodyTypeToSelect = BodySelectionType.GETTER
                    } else if (!getter.hasBody()) {
                        getter = getter.replace(templateGetter) as KtPropertyAccessor
                        bodyTypeToSelect = BodySelectionType.GETTER
                    }

                    if (extension.isVar) {
                        var setter = extension.setter
                        if (setter == null) {
                            setter = extension.addAfter(templateSetter, getter) as KtPropertyAccessor
                            extension.addBefore(psiFactory.createNewLine(), setter)
                            if (bodyTypeToSelect == BodySelectionType.NOTHING) {
                                bodyTypeToSelect = BodySelectionType.SETTER
                            }
                        } else if (!setter.hasBody()) {
                            setter.replace(templateSetter) as KtPropertyAccessor
                            if (bodyTypeToSelect == BodySelectionType.NOTHING) {
                                bodyTypeToSelect = BodySelectionType.SETTER
                            }
                        }
                    }
                }
            }
        }
        extension to bodyTypeToSelect
    }

    if (ktFilesToAddImports.isNotEmpty()) {
        extension.fqName?.let { fqName ->
            runWriteAction {
                for (ktFile in ktFilesToAddImports) {
                    addImport(ktFile, fqName)
                }
            }
        }
    }

    if (javaCallsToFix.isNotEmpty()) {
        val lightMethod = extension.toLightMethods().first()
        for (javaCallToFix in javaCallsToFix) {
            runWriteAction {
                javaCallToFix.methodExpression.qualifierExpression?.let {
                    val argumentList = javaCallToFix.argumentList
                    argumentList.addBefore(it, argumentList.expressions.firstOrNull())
                }

                val newRef = javaCallToFix.methodExpression.bindToElement(lightMethod)
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(newRef)
            }
        }
    }

    return extension to bodyTypeToSelect
}

private fun newTypeParameterList(member: KtCallableDeclaration): KtTypeParameterList? {
    val classElement =
        member.parents.match(KtClassBody::class, last = KtClassOrObject::class) ?: error("Can't typeMatch ${member.parent.parent}")
    val classParams = classElement.typeParameters
    if (classParams.isEmpty()) return null
    val allTypeParameters = classParams + member.typeParameters
    val text = allTypeParameters.joinToString(",", "<", ">") { it.textWithoutVariance() }
    return org.jetbrains.kotlin.psi.KtPsiFactory(member.project).createDeclaration<KtFunction>("fun $text foo()").typeParameterList
}

private fun KtTypeParameter.textWithoutVariance(): String {
    if (variance == Variance.INVARIANT) return text
    val copied = this.copy() as KtTypeParameter
    copied.modifierList?.getModifier(KtTokens.OUT_KEYWORD)?.delete()
    copied.modifierList?.getModifier(KtTokens.IN_KEYWORD)?.delete()
    return copied.text
}

private fun KtCallableDeclaration.setReceiverType(klass: KtClassOrObject) {
    val className = buildString {
        append(klass.name)
        if (klass.typeParameters.isNotEmpty()) {
            append(klass.typeParameters.joinToString(", ", "<", ">") {
                it.name ?: ""
            })
        }
    }
    val typeReference = KtPsiFactory.contextual(this@setReceiverType).createType(className)
    val receiverReference = setReceiverTypeReference(typeReference) ?: return
    ShortenReferencesFacility.getInstance().shorten(receiverReference)
}
