// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.sealedSubClassToObject

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgressIfEdt
import org.jetbrains.kotlin.idea.intentions.ConvertSecondaryConstructorToPrimaryIntention
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.buildExpression
import org.jetbrains.kotlin.psi.createDeclarationByPattern
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class ConvertSealedSubClassToObjectFix : LocalQuickFix {

    override fun getFamilyName() = KotlinBundle.message("convert.sealed.subclass.to.object.fix.family.name")

    override fun startInWriteAction(): Boolean = false

    companion object {
        val JAVA_LANG = Language.findLanguageByID("JAVA")
        val KOTLIN_LANG = Language.findLanguageByID("kotlin")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val klass = descriptor.psiElement.getParentOfType<KtClass>(false) ?: return
        val ktClassSmartPointer: SmartPsiElementPointer<KtClass> = SmartPointerManager.createPointer(klass)

        changeInstances(ktClassSmartPointer)
        changeDeclaration(ktClassSmartPointer)
    }

    /**
     * Changes declaration of class to object.
     */
    private fun changeDeclaration(pointer: SmartPsiElementPointer<KtClass>) {
        runWriteAction {
            val element = pointer.element ?: return@runWriteAction
            val psiFactory = KtPsiFactory(element.project)

            element.changeToObject(psiFactory)
            element.transformToObject(psiFactory)
        }
    }

    private fun KtClass.changeToObject(factory: KtPsiFactory) {
        secondaryConstructors.forEach { ConvertSecondaryConstructorToPrimaryIntention().applyTo(it, null) }
        primaryConstructor?.delete()
        getClassOrInterfaceKeyword()?.replace(
            if (!isData() && languageVersionSettings.supportsFeature(LanguageFeature.DataObjects))
                factory.createDeclarationByPattern("${KtTokens.DATA_KEYWORD.value} ${KtTokens.OBJECT_KEYWORD.value}")
            else
                factory.createExpression(KtTokens.OBJECT_KEYWORD.value)
        )
    }

    private fun KtClass.transformToObject(factory: KtPsiFactory) {
        replace(factory.createObject(text))
    }

    /**
     * Replace instantiations of the class with links to the singleton instance of the object.
     */
    private fun changeInstances(pointer: SmartPsiElementPointer<KtClass>) {
        mapReferencesByLanguage(pointer)
            .apply {
                runWriteAction {
                    val ktClass = pointer.element ?: return@runWriteAction
                    replaceKotlin(ktClass)
                    replaceJava(ktClass)
                }
            }
    }

    /**
     * Map references to this class by language
     */
    private fun mapReferencesByLanguage(pointer: SmartPsiElementPointer<KtClass>): Map<Language, List<PsiElement>> =
        pointer.project.runSynchronouslyWithProgressIfEdt(KotlinBundle.message("progress.looking.up.sealed.subclass.usage"), true) {
            pointer.element?.let { ktClass ->
                ReferencesSearch.search(ktClass).groupBy({ it.element.language }, { it.element.parent })
            } ?: emptyMap()
        } ?: emptyMap()

    /**
     * Replace Kotlin instantiations to a straightforward call to the singleton.
     */
    private fun Map<Language, List<PsiElement>>.replaceKotlin(klass: KtClass) {
        val list = this[KOTLIN_LANG] ?: return
        val singletonCall = KtPsiFactory(klass.project).buildExpression { appendName(klass.nameAsSafeName) }

        list.filter { it.node.elementType == KtNodeTypes.CALL_EXPRESSION }
            .forEach { it.replace(singletonCall) }
    }

    /**
     * Replace Java instantiations to an instance of the object, unless it is the only thing
     * done in the statement, in which IDEA will consider wrong, so I delete the line.
     */
    private fun Map<Language, List<PsiElement>>.replaceJava(klass: KtClass) {
        val list = this[JAVA_LANG] ?: return
        val first = list.firstOrNull() ?: return
        val elementFactory = JavaPsiFacade.getElementFactory(klass.project)
        val javaSingletonCall = elementFactory.createExpressionFromText("${klass.name}.INSTANCE", first)

        list.filter { it.node.elementType == JavaElementType.NEW_EXPRESSION }
            .forEach {
                when (it.parent.node.elementType) {
                    JavaElementType.EXPRESSION_STATEMENT -> it.delete()
                    else -> it.replace(javaSingletonCall)
                }
            }
    }
}