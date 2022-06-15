// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.psi

import com.google.common.collect.HashMultimap
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.annotations.JVM_STATIC_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.kotlin.resolve.jvm.annotations.JVM_OVERLOADS_FQ_NAME

object KotlinPsiHeuristics {
    @JvmStatic
    fun unwrapImportAlias(file: KtFile, aliasName: String): Collection<String> {
        return file.aliasImportMap[aliasName]
    }

    @JvmStatic
    fun unwrapImportAlias(type: KtUserType, aliasName: String): Collection<String> {
        val file = type.containingKotlinFileStub?.psi as? KtFile ?: return emptyList()
        return unwrapImportAlias(file, aliasName)
    }

    @JvmStatic
    fun getImportAliases(file: KtFile, names: Set<String>): Set<String> {
        val result = LinkedHashSet<String>()
        for ((aliasName, name) in file.aliasImportMap.entries()) {
            if (name in names) {
                result += aliasName
            }
        }
        return result
    }

    private val KtFile.aliasImportMap by userDataCached("ALIAS_IMPORT_MAP_KEY") { file ->
        HashMultimap.create<String, String>().apply {
            for (import in file.importList?.imports.orEmpty()) {
                val aliasName = import.aliasName ?: continue
                val name = import.importPath?.fqName?.shortName()?.asString() ?: continue
                put(aliasName, name)
            }
        }
    }

    @JvmStatic
    fun isProbablyNothing(typeReference: KtTypeReference): Boolean {
        val userType = typeReference.typeElement as? KtUserType ?: return false
        return isProbablyNothing(userType)
    }

    @JvmStatic
    fun isProbablyNothing(type: KtUserType): Boolean {
        val referencedName = type.referencedName

        if (referencedName == "Nothing") {
            return true
        }

        // TODO: why don't use PSI-less stub for calculating aliases?
        val file = type.containingKotlinFileStub?.psi as? KtFile ?: return false

        // TODO: support type aliases
        return file.aliasImportMap[referencedName].contains("Nothing")
    }

    @JvmStatic
    fun getJvmName(fqName: FqName): String {
        val asString = fqName.asString()
        var startIndex = 0
        while (startIndex != -1) { // always true
            val dotIndex = asString.indexOf('.', startIndex)
            if (dotIndex == -1) return asString

            startIndex = dotIndex + 1
            val charAfterDot = asString.getOrNull(startIndex) ?: return asString
            if (!charAfterDot.isLetter()) return asString
            if (charAfterDot.isUpperCase()) return buildString {
                append(asString.subSequence(0, startIndex))
                append(asString.substring(startIndex).replace('.', '$'))
            }
        }

        return asString
    }

    @JvmStatic
    fun getJvmName(declaration: KtClassOrObject): String? {
        val classId = declaration.classIdIfNonLocal ?: return null
        val jvmClassName = JvmClassName.byClassId(classId)
        return jvmClassName.fqNameForTopLevelClassMaybeWithDollars.asString()
    }

    @JvmStatic
    fun findAnnotation(declaration: KtAnnotated, shortName: String, useSiteTarget: AnnotationUseSiteTarget? = null): KtAnnotationEntry? {
        return declaration.annotationEntries
            .firstOrNull { it.useSiteTarget?.getAnnotationUseSiteTarget() == useSiteTarget && it.shortName?.asString() == shortName }
    }

    @JvmStatic
    fun hasAnnotation(declaration: KtAnnotated, shortName: String, useSiteTarget: AnnotationUseSiteTarget? = null): Boolean {
        return findAnnotation(declaration, shortName, useSiteTarget) != null
    }

    @JvmStatic
    fun hasAnnotation(declaration: KtAnnotated, shortName: Name, useSiteTarget: AnnotationUseSiteTarget? = null): Boolean {
        return findAnnotation(declaration, shortName.asString(), useSiteTarget) != null
    }

    @JvmStatic
    fun findJvmName(declaration: KtAnnotated, useSiteTarget: AnnotationUseSiteTarget? = null): String? {
        val annotation = findAnnotation(declaration, JvmFileClassUtil.JVM_NAME_SHORT, useSiteTarget) ?: return null
        return JvmFileClassUtil.getLiteralStringFromAnnotation(annotation)
    }

    @JvmStatic
    fun findJvmGetterName(declaration: KtValVarKeywordOwner): String? {
        return when (declaration) {
            is KtProperty -> declaration.getter?.let(::findJvmName) ?: findJvmName(declaration, AnnotationUseSiteTarget.PROPERTY_GETTER)
            is KtParameter -> findJvmName(declaration, AnnotationUseSiteTarget.PROPERTY_GETTER)
            else -> null
        }
    }

    @JvmStatic
    fun findJvmSetterName(declaration: KtValVarKeywordOwner): String? {
        return when (declaration) {
            is KtProperty -> declaration.setter?.let(::findJvmName) ?: findJvmName(declaration, AnnotationUseSiteTarget.PROPERTY_SETTER)
            is KtParameter -> findJvmName(declaration, AnnotationUseSiteTarget.PROPERTY_SETTER)
            else -> null
        }
    }

    @JvmStatic
    fun hasJvmFieldAnnotation(declaration: KtAnnotated): Boolean {
        return hasAnnotation(declaration, JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME.shortName())
    }

    @JvmStatic
    fun hasJvmOverloadsAnnotation(declaration: KtAnnotated): Boolean {
        return hasAnnotation(declaration, JVM_OVERLOADS_FQ_NAME.shortName())
    }

    @JvmStatic
    fun hasJvmStaticAnnotation(declaration: KtAnnotated): Boolean {
        return hasAnnotation(declaration, JVM_STATIC_ANNOTATION_FQ_NAME.shortName())
    }
}