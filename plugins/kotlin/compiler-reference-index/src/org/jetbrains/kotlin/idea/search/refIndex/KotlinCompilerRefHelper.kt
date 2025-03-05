// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.backwardRefs.LanguageCompilerRefAdapter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.NameEnumerator
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasBody
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember

class KotlinCompilerRefHelper : LanguageCompilerRefAdapter.ExternalLanguageHelper() {
    override fun getAffectedFileTypes(): Set<FileType> = setOf(KotlinFileType.INSTANCE)
    override fun asCompilerRef(element: PsiElement, names: NameEnumerator): CompilerRef? = asCompilerRefs(element, names)?.singleOrNull()
    override fun asCompilerRefs(element: PsiElement, names: NameEnumerator): List<CompilerRef>? =
        when (val originalElement = element.unwrapped) {
            is KtEnumEntry -> originalElement.asEnumCompilerRefs(names)
            is KtClass -> originalElement.asClassCompilerRef(names)?.let(::listOf)
            is KtObjectDeclaration -> originalElement.asObjectCompilerRefs(names)
            is KtConstructor<*> -> originalElement.asConstructorCompilerRef(names)
            is KtCallableDeclaration -> originalElement.asCallableCompilerRefs(names)
            else -> null
        }

    override fun isTooCommonLibraryElement(element: PsiElement): Boolean = runReadAction {
        val ktClassOrObject = when (element) {
            is KtClassOrObject -> element
            is KtCallableDeclaration -> element.containingClassOrObject
            else -> null
        }

        ktClassOrObject?.fqName?.asString() == StandardNames.FqNames.any.asString()
    }

    override fun getHierarchyRestrictedToLibraryScope(
        baseRef: CompilerRef,
        basePsi: PsiElement,
        names: NameEnumerator,
        libraryScope: GlobalSearchScope
    ): List<CompilerRef> {
        val baseClass = when (basePsi) {
            is KtClassOrObject, is PsiClass -> basePsi
            is PsiMember -> basePsi.containingClass
            is KtCallableDeclaration -> basePsi.containingClassOrObject?.takeUnless { it is KtObjectDeclaration }
            else -> null
        } ?: return emptyList()

        val overridden = mutableListOf<CompilerRef>()
        val processor = Processor { psiClass: PsiClass ->
            psiClass.takeUnless { it.hasModifierProperty(PsiModifier.PRIVATE) }
                ?.let { runReadAction { it.qualifiedName } }
                ?.let { names.tryEnumerate(it) }
                ?.let { overridden.add(baseRef.override(it)) }

            true
        }

        HierarchySearchRequest(
            originalElement = baseClass,
            searchScope = libraryScope,
            searchDeeply = true,
        ).searchInheritors().forEach(processor)

        return overridden
    }
}

private fun KtEnumEntry.asEnumCompilerRefs(enumerator: NameEnumerator): List<CompilerRef>? {
    val containingClassOrObject = containingClassOrObject ?: return null
    val owner = containingClassOrObject.asClassCompilerRef(enumerator) ?: return null
    val name = name ?: return null
    return listOf(owner.createField(enumerator.tryEnumerate(name)))
}

private fun KtCallableDeclaration.asCallableCompilerRefs(names: NameEnumerator): List<CompilerRef>? {
    if (isTopLevelKtOrJavaMember()) return asTopLevelCompilerRefs(names)
    val containingClassOrObject = containingClassOrObject ?: return null
    return when (containingClassOrObject) {
        is KtClass -> asClassMemberCompilerRefs(containingClassOrObject, names)
        is KtObjectDeclaration -> asObjectMemberCompilerRefs(containingClassOrObject, names)
        else -> null
    }
}

private fun KtCallableDeclaration.asClassMemberCompilerRefs(
    containingClass: KtClass,
    names: NameEnumerator,
): List<CompilerRef>? = when (this) {
    is KtNamedFunction -> asClassMemberFunctionCompilerRefs(containingClass, names)
    is KtProperty -> asClassMemberPropertyCompilerRefs(containingClass, names)
    is KtParameter -> asParameterCompilerRefs(containingClass, names)
    else -> null
}

private fun KtCallableDeclaration.asObjectMemberCompilerRefs(
    containingObject: KtObjectDeclaration,
    names: NameEnumerator,
): List<CompilerRef>? = when (this) {
    is KtNamedFunction -> asObjectMemberFunctionCompilerRefs(containingObject, names)
    is KtProperty -> asObjectMemberPropertyCompilerRefs(containingObject, names)
    else -> null
}

private fun KtCallableDeclaration.asTopLevelCompilerRefs(names: NameEnumerator): List<CompilerRef.CompilerMember>? =
    containingKtFile.javaFileFacadeFqName.asString().asClassCompilerRef(names).let { owner ->
        when (this) {
            is KtNamedFunction -> asFunctionCompilerRefs(owner, names)
            is KtProperty -> asPropertyCompilerRefs(owner, names)
            else -> null
        }
    }

private fun String.asClassCompilerRef(
    names: NameEnumerator,
): CompilerClassHierarchyElementDefWithSearchId = JavaCompilerClassRefWithSearchId.create(this, names)

private fun KtClassOrObject.asClassCompilerRef(
    names: NameEnumerator,
): CompilerClassHierarchyElementDefWithSearchId? = JavaCompilerClassRefWithSearchId.create(this, names)

private fun KtObjectDeclaration.asObjectCompilerRefs(names: NameEnumerator): List<CompilerRef.NamedCompilerRef>? {
    val classCompilerRef = asClassCompilerRef(names) ?: return null
    val instanceField = if (isCompanion()) {
        asCompanionCompilerRef(names)
    } else {
        classCompilerRef.createField(names.tryEnumerate("INSTANCE"))
    }

    return listOfNotNull(classCompilerRef, instanceField)
}

private fun KtObjectDeclaration.asCompanionCompilerRef(names: NameEnumerator): CompilerRef.NamedCompilerRef? {
    val name = name ?: return null
    val owner = containingClassOrObject?.asClassCompilerRef(names) ?: return null
    return owner.createField(names.tryEnumerate(name))
}

private fun KtConstructor<*>.asConstructorCompilerRef(names: NameEnumerator): List<CompilerRef.CompilerMember>? {
    val owner = getContainingClassOrObject().asClassCompilerRef(names) ?: return null
    val nameId = names.tryEnumerate("<init>")
    return asCompilerRefsWithJvmOverloads(owner, nameId)
}

private fun KtNamedFunction.asFunctionCompilerRefs(
    owner: CompilerClassHierarchyElementDefWithSearchId,
    names: NameEnumerator,
    isDefaultImplsMember: Boolean = false,
): List<CompilerRef.CompilerMember> {
    val jvmName = KotlinPsiHeuristics.findJvmName(this) ?: name
    val nameId = names.tryEnumerate(jvmName)
    return asCompilerRefsWithJvmOverloads(owner, nameId, isDefaultImplsMember)
}

private fun KtNamedFunction.asClassMemberFunctionCompilerRefs(
    containingClass: KtClass,
    names: NameEnumerator,
): List<CompilerRef>? = asClassMemberCompilerRefs(
    containingClass = containingClass,
    names = names,
    methodHandler = { owner -> asFunctionCompilerRefs(owner, names) },
    defaultMethodHandler = { owner -> asFunctionCompilerRefs(owner, names, isDefaultImplsMember = true) },
)

private fun KtProperty.asClassMemberPropertyCompilerRefs(
    containingClass: KtClass,
    names: NameEnumerator,
): List<CompilerRef>? = asClassMemberCompilerRefs(
    containingClass = containingClass,
    names = names,
    methodHandler = { owner -> asPropertyCompilerRefs(owner, names) },
    defaultMethodHandler = { owner -> asPropertyCompilerRefs(owner, names, fieldOwner = null, isDefaultImplsMember = true) },
)

private fun KtCallableDeclaration.asClassMemberCompilerRefs(
    containingClass: KtClass,
    names: NameEnumerator,
    methodHandler: (owner: CompilerClassHierarchyElementDefWithSearchId) -> List<CompilerRef>?,
    defaultMethodHandler: (owner: CompilerClassHierarchyElementDefWithSearchId) -> List<CompilerRef>? = methodHandler,
): List<CompilerRef>? {
    val owner = containingClass.asClassCompilerRef(names) ?: return null
    val compilerMembers = methodHandler(owner)
    if (!containingClass.isInterface() || !hasBody()) return compilerMembers

    val defaultImplQualifier = owner.jvmClassName + JvmAbi.DEFAULT_IMPLS_SUFFIX
    val defaultImplMembers = defaultMethodHandler(defaultImplQualifier.asClassCompilerRef(names))
        ?: return compilerMembers

    return compilerMembers?.plus(defaultImplMembers) ?: defaultImplMembers
}

private fun KtNamedFunction.asObjectMemberFunctionCompilerRefs(
    containingObject: KtObjectDeclaration,
    names: NameEnumerator,
): List<CompilerRef.CompilerMember>? {
    val owner = containingObject.asClassCompilerRef(names) ?: return null
    val compilerMembers = asFunctionCompilerRefs(owner, names)
    val additionalOwner = containingObject.takeIf { KotlinPsiHeuristics.hasJvmStaticAnnotation(this) }
        ?.containingClassOrObject
        ?.asClassCompilerRef(names)
        ?: return compilerMembers

    return compilerMembers + asFunctionCompilerRefs(additionalOwner, names)
}

private fun KtFunction.asCompilerRefsWithJvmOverloads(
    owner: CompilerClassHierarchyElementDefWithSearchId,
    nameId: Int,
    isDefaultImplsMember: Boolean = false,
): List<CompilerRef.CompilerMember> {
    val numberOfArguments = getJvmArgumentCount(countReceiver = true) + (1.takeIf { isDefaultImplsMember } ?: 0)
    if (!KotlinPsiHeuristics.hasJvmOverloadsAnnotation(this)) {
        val mainMethodRef = owner.createMethod(nameId, numberOfArguments)
        return if (this is KtPrimaryConstructor && valueParameters.all(KtParameter::hasDefaultValue)) {
            listOf(mainMethodRef, owner.createMethod(nameId, 0))
        } else {
            listOf(mainMethodRef)
        }
    }

    return numberOfArguments.minus(valueParameters.count(KtParameter::hasDefaultValue)).rangeTo(numberOfArguments).map {
        owner.createMethod(nameId, it)
    }
}

private fun KtProperty.asObjectMemberPropertyCompilerRefs(
    containingObject: KtObjectDeclaration,
    names: NameEnumerator,
): List<CompilerRef.CompilerMember>? {
    val owner = containingObject.asClassCompilerRef(names) ?: return null
    if (!containingObject.isCompanion()) {
        return asPropertyCompilerRefs(owner, names)
    }

    val fieldOwner = containingObject.containingClassOrObject?.asClassCompilerRef(names)
    val compilerMembers = asPropertyCompilerRefs(owner, names, fieldOwner)
    if (!KotlinPsiHeuristics.hasJvmStaticAnnotation(this) || fieldOwner == null) return compilerMembers

    val staticMembers = asPropertyCompilerRefs(fieldOwner, names, fieldOwner = null) ?: return compilerMembers
    return compilerMembers?.plus(staticMembers) ?: staticMembers
}

private fun KtProperty.asPropertyCompilerRefs(
    owner: CompilerClassHierarchyElementDefWithSearchId,
    names: NameEnumerator,
    fieldOwner: CompilerClassHierarchyElementDefWithSearchId? = owner,
    isDefaultImplsMember: Boolean = false,
): List<CompilerRef.CompilerMember>? = asPropertyOrParameterCompilerRefs(owner, names, isVar, fieldOwner, isDefaultImplsMember)

private fun KtParameter.asParameterCompilerRefs(
    containingClass: KtClass,
    names: NameEnumerator,
): List<CompilerRef.CompilerMember>? {
    if (!hasValOrVar()) return null

    val owner = containingClass.asClassCompilerRef(names) ?: return null
    if (containingClassOrObject?.isAnnotation() == true) {
        val name = name ?: return null
        return listOf(owner.createMethod(names.tryEnumerate(name), 0))
    }

    val compilerMembers = asPropertyOrParameterCompilerRefs(owner, names, isMutable)
    val componentFunctionMember = asComponentFunctionName?.let {
        owner.createMethod(names.tryEnumerate(it), 0)
    } ?: return compilerMembers

    return compilerMembers?.plus(componentFunctionMember) ?: listOf(componentFunctionMember)
}

private fun <T> T.asPropertyOrParameterCompilerRefs(
    owner: CompilerClassHierarchyElementDefWithSearchId,
    names: NameEnumerator,
    isMutable: Boolean,
    fieldOwner: CompilerClassHierarchyElementDefWithSearchId? = owner,
    isDefaultImplsMember: Boolean = false,
): List<CompilerRef.CompilerMember>? where T : KtCallableDeclaration, T : KtValVarKeywordOwner {
    val name = name ?: return null
    if (fieldOwner != null && (hasModifier(KtTokens.CONST_KEYWORD) || KotlinPsiHeuristics.hasJvmFieldAnnotation(this))) {
        return listOf(fieldOwner.createField(names.tryEnumerate(name)))
    }

    val field = if (fieldOwner != null && hasModifier(KtTokens.LATEINIT_KEYWORD)) {
        fieldOwner.createField(names.tryEnumerate(name))
    } else {
        null
    }

    val numberOfArguments = getJvmArgumentCount(countReceiver = true) + (1.takeIf { isDefaultImplsMember } ?: 0)
    val jvmGetterName = KotlinPsiHeuristics.findJvmGetterName(this) ?: JvmAbi.getterName(name)
    val getter = owner.createMethod(names.tryEnumerate(jvmGetterName), numberOfArguments)

    val setter = if (isMutable) {
        val jvmSetterName = KotlinPsiHeuristics.findJvmSetterName(this) ?: JvmAbi.setterName(name)
        owner.createMethod(names.tryEnumerate(jvmSetterName), numberOfArguments + 1)
    } else {
        null
    }

    return listOfNotNull(field, getter, setter)
}

private fun KtCallableDeclaration.getJvmArgumentCount(countReceiver: Boolean = false): Int {
    return valueParameters.size + (if (countReceiver && receiverTypeReference != null) 1 else 0)
}