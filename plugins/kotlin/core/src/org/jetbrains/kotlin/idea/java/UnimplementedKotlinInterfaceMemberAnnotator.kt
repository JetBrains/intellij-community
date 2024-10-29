// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.java

import com.intellij.codeInsight.ClassUtil.getAnyMethodToImplement
import com.intellij.codeInsight.daemon.JavaErrorBundle
import com.intellij.codeInsight.daemon.impl.analysis.HighlightNamesUtil
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.KtLightClassMarker
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.config.JvmAnalysisFlags
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.load.java.structure.LightClassOriginKind
import org.jetbrains.kotlin.resolve.annotations.JVM_STATIC_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.resolve.jvm.annotations.JVM_DEFAULT_FQ_NAME
import org.jetbrains.kotlin.utils.ifEmpty

class UnimplementedKotlinInterfaceMemberAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiClass || element.language == KotlinLanguage.INSTANCE) return

        if (element.isInterface || element.hasModifierProperty(PsiModifier.ABSTRACT)) return

        if (getAnyMethodToImplement(element) != null) return // reported by java default annotator

        findUnimplementedMethod(element)?.let {
            report(it, holder, element)
        }
    }

    private fun findUnimplementedMethod(psiClass: PsiClass): KtLightMethod? {
        val signaturesFromKotlinInterfaces = psiClass.visibleSignatures.filter { signature ->
            val method = signature.method
            method is KtLightMethod &&
                    method.hasModifierProperty(PsiModifier.DEFAULT) &&
                    !method.hasModifierProperty(PsiModifier.STATIC)
        }.ifEmpty { return null }

        val kotlinSuperClass = generateSequence(psiClass) { it.superClass }.firstOrNull { it is KtLightClassForSourceDeclaration }

        val signaturesVisibleThroughKotlinSuperClass = kotlinSuperClass?.visibleSignatures ?: emptyList()
        return signaturesFromKotlinInterfaces
            .firstOrNull { shouldBeImplemented(it, signaturesVisibleThroughKotlinSuperClass) }
            ?.method as? KtLightMethod
    }

    private fun shouldBeImplemented(
        method: HierarchicalMethodSignature,
        signaturesVisibleThroughKotlinSuperClass: Collection<HierarchicalMethodSignature>
    ): Boolean {
        if (method in signaturesVisibleThroughKotlinSuperClass) return false

        val psiMethod = method.method
        if (psiMethod.isBinaryOrigin) return false

        val hasJvmDefaultOrJvmStatic = psiMethod.modifierList.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == JVM_DEFAULT_FQ_NAME.asString() || qualifiedName == JVM_STATIC_ANNOTATION_FQ_NAME.asString()
        }

        if (hasJvmDefaultOrJvmStatic) return false

        val jvmDefaultMode = psiMethod.languageVersionSettings.getFlag(JvmAnalysisFlags.jvmDefaultMode)
        return !jvmDefaultMode.isEnabled
    }

    private val PsiMethod.isBinaryOrigin get() = (containingClass as? KtLightClassMarker)?.originKind == LightClassOriginKind.BINARY

    private fun report(method: KtLightMethod, holder: AnnotationHolder, psiClass: PsiClass) {
        val key = if (psiClass is PsiEnumConstantInitializer) "enum.constant.should.implement.method" else "class.must.be.abstract"
        val message = JavaErrorBundle.message(
            key, HighlightUtil.formatClass(psiClass, false), JavaHighlightUtil.formatMethod(method),
            HighlightUtil.formatClass(method.containingClass, false)
        )
        val quickFixFactory = QuickFixFactory.getInstance()
        var error = holder.newAnnotation(HighlightSeverity.ERROR, message)
            .range(HighlightNamesUtil.getClassDeclarationTextRange(psiClass))
            .withFix(quickFixFactory.createImplementMethodsFix(psiClass))
        // this code is untested
        // see com.intellij.codeInsight.daemon.impl.analysis.HighlightClassUtil.checkClassWithAbstractMethods
        if (psiClass !is PsiAnonymousClass && psiClass.modifierList?.hasExplicitModifier(PsiModifier.FINAL) != true) {
            error = error.withFix(quickFixFactory.createModifierListFix(psiClass, PsiModifier.ABSTRACT, true, false))
        }
        error.create()
    }
}
