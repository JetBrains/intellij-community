// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.cfg.pseudocode.containingDeclarationForPseudocode
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

fun KtElement.getElementTextInContext(): String {
    val context = parentOfType<KtImportDirective>()
        ?: parentOfType<KtPackageDirective>()
        ?: containingDeclarationForPseudocode
        ?: containingKtFile
    val builder = StringBuilder()
    context.accept(object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element === this@getElementTextInContext) builder.append("<$ELEMENT_TAG>")
            if (element is LeafPsiElement) {
                builder.append(element.text)
            } else {
                element.acceptChildren(this)
            }
            if (element === this@getElementTextInContext) builder.append("</$ELEMENT_TAG>")
        }
    })
    return builder.toString().trimIndent().trim()
}

private const val ELEMENT_TAG = "ELEMENT"

fun KtClassOrObject.classIdIfNonLocal(): ClassId? {
    if (KtPsiUtil.isLocal(this)) return null
    val packageName = containingKtFile.packageFqName
    val classesNames = parentsOfType<KtDeclaration>().map { it.name }.toList().asReversed()
    if (classesNames.any { it == null }) return null
    return ClassId(packageName, FqName(classesNames.joinToString(separator = ".")), /*local=*/false)
}

val KtClassOrObject.jvmFqName: String?
    get() = classIdIfNonLocal()?.let { JvmClassName.byClassId(it) }?.fqNameForTopLevelClassMaybeWithDollars?.asString()

fun PsiElement.reformatted(canChangeWhiteSpacesOnly: Boolean = false): PsiElement = let {
    CodeStyleManager.getInstance(it.project).reformat(it, canChangeWhiteSpacesOnly)
}

fun KtAnnotated.findAnnotation(
    shortName: String,
    useSiteTarget: AnnotationUseSiteTarget? = null,
): KtAnnotationEntry? = annotationEntries.firstOrNull {
    it.useSiteTarget?.getAnnotationUseSiteTarget() == useSiteTarget && it.shortName?.asString() == shortName
}

private fun KtAnnotated.findJvmName(useSiteTarget: AnnotationUseSiteTarget? = null): String? =
    findAnnotation(JvmFileClassUtil.JVM_NAME_SHORT, useSiteTarget)?.let(JvmFileClassUtil::getLiteralStringFromAnnotation)

val KtNamedFunction.jvmName: String? get() = findJvmName()
val KtPropertyAccessor.jvmName: String? get() = findJvmName()
val KtProperty.jvmSetterName: String? get() = setter?.jvmName ?: findJvmName(AnnotationUseSiteTarget.PROPERTY_SETTER)
val KtProperty.jvmGetterName: String? get() = getter?.jvmName ?: findJvmName(AnnotationUseSiteTarget.PROPERTY_GETTER)

fun KtCallableDeclaration.numberOfArguments(countReceiver: Boolean = false): Int =
    valueParameters.size + (1.takeIf { countReceiver && receiverTypeReference != null } ?: 0)
