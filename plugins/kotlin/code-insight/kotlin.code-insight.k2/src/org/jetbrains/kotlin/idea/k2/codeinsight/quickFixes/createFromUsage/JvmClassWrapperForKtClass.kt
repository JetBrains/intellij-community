// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * A [JvmClass] whose only purpose is to represent a [KtClassOrObject]. Note that this class does not have any information
 * other than [JvmClassWrapperForKtClass.getSourceElement] that simply returns [ktClassOrFile]. For example, there is no guarantee
 * that [JvmClassWrapperForKtClass.getTypeParameters] returns any type parameter information.
 *
 * Note that our "request and creation" architecture will support the cross language requests and creations like J2K, K2K, and so on.
 * For the cross language support, we use [JvmClass]. However, for K2K, the conversion between [KtClassOrObject] and [JvmClass] is not
 * unnecessary because both the request and creation sides use [KtClassOrObject]. This class helps us to avoid the unnecessary conversion.
 */
internal class JvmClassWrapperForKtClass<T: KtElement>(internal val ktClassOrFile: T) : JvmClass {
    override fun getSourceElement(): PsiElement = ktClassOrFile

    override fun getAnnotations(): Array<JvmAnnotation> = emptyArray()

    override fun hasModifier(modifier: JvmModifier): Boolean = false

    override fun getName(): String? = ktClassOrFile.name

    override fun getContainingClass(): JvmClass? = null

    override fun getTypeParameters(): Array<JvmTypeParameter> = emptyArray()

    override fun getQualifiedName(): String? = when (ktClassOrFile) {
        is KtClassOrObject -> ktClassOrFile.fqName?.asString()
        is KtFile -> ktClassOrFile.name
        else -> null
    }

    override fun getClassKind(): JvmClassKind = JvmClassKind.CLASS

    override fun getSuperClassType(): JvmReferenceType? = null

    override fun getInterfaceTypes(): Array<JvmReferenceType> = emptyArray()

    override fun getMethods(): Array<JvmMethod> = emptyArray()

    override fun getFields(): Array<JvmField> = emptyArray()

    override fun getInnerClasses(): Array<JvmClass> = emptyArray()
}