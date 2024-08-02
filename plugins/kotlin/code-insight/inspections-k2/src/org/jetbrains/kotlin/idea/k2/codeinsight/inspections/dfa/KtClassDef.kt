// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa

import com.intellij.codeInspection.dataFlow.TypeConstraint
import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import java.util.stream.Stream

class KtClassDef(
    private val module: KaModule,
    private val hash: Int,
    val pointer: KaSymbolPointer<KaClassSymbol>,
    private val kind: KaClassKind,
    private val modality: KaSymbolModality?
) : TypeConstraints.ClassDef {
    override fun isInheritor(superClassQualifiedName: String): Boolean =
        analyze(module) {
            val classLikeSymbol = pointer.restoreSymbol() ?: return@analyze false
            classLikeSymbol.superTypes.any { superType ->
                (superType as? KaClassType)?.expandedSymbol?.classId?.asFqNameString() == superClassQualifiedName
            }
        }

    override fun isInheritor(superType: TypeConstraints.ClassDef): Boolean =
        superType is KtClassDef && analyze(module) {
            val classLikeSymbol = pointer.restoreSymbol() ?: return@analyze false
            val superSymbol = superType.pointer.restoreSymbol() ?: return@analyze false
            classLikeSymbol.isSubClassOf(superSymbol)
        }

    override fun isConvertible(other: TypeConstraints.ClassDef): Boolean {
        if (other !is KtClassDef) return false
        // TODO: support sealed
        if (isInterface && other.isInterface) return true
        if (isInterface && !other.isFinal) return true
        if (other.isInterface && !isFinal) return true
        return isInheritor(other) || other.isInheritor(this)
    }

    override fun isInterface(): Boolean = kind == KaClassKind.INTERFACE || kind == KaClassKind.ANNOTATION_CLASS

    override fun isEnum(): Boolean = kind == KaClassKind.ENUM_CLASS

    override fun isFinal(): Boolean = kind != KaClassKind.ANNOTATION_CLASS && modality == KaSymbolModality.FINAL

    override fun isAbstract(): Boolean = modality == KaSymbolModality.ABSTRACT

    override fun getEnumConstant(ordinal: Int): PsiEnumConstant? = analyze(module) {
        val classLikeSymbol = pointer.restoreSymbol() ?: return@analyze null
        val psiClass = when (val psi = classLikeSymbol.psi) {
            is PsiClass -> psi
            is KtClassOrObject -> psi.toLightClass() ?: return@analyze null
            else -> return@analyze null
        }
        var cur = 0
        for (field in psiClass.fields) {
            if (field is PsiEnumConstant) {
                if (cur == ordinal) return@analyze field
                cur++
            }
        }
        return@analyze null
    }

    override fun getQualifiedName(): String? = analyze(module) {
        val fqNameUnsafe = pointer.restoreSymbol()?.classId?.asSingleFqName()?.toUnsafe() ?: return@analyze null
        correctFqName(fqNameUnsafe)
    }

    override fun superTypes(): Stream<TypeConstraints.ClassDef> =
        analyze(module) {
            val classLikeSymbol = pointer.restoreSymbol() ?: return@analyze Stream.empty<TypeConstraints.ClassDef>()
            val list: List<TypeConstraints.ClassDef> = classLikeSymbol.superTypes.asSequence()
                .filterIsInstance<KaClassType>()
                .mapNotNull { type -> type.expandedSymbol }
                .map { symbol -> symbol.classDef() }
                .toList()
            @Suppress("SSBasedInspection")
            list.stream()
        }

    @OptIn(KaExperimentalApi::class)
    override fun toPsiType(project: Project): PsiType? =
        analyze(module) {
            val classLikeSymbol = pointer.restoreSymbol() ?: return@analyze null
            val psi = classLikeSymbol.psi ?: return@analyze null
            buildClassType(classLikeSymbol).asPsiType(psi, true)
        }

    override fun equals(other: Any?): Boolean {
        return other is KtClassDef && other.pointer.pointsToTheSameSymbolAs(pointer)
    }

    override fun hashCode(): Int = hash

    override fun toString(): String = qualifiedName ?: "<unnamed>"

    private fun correctFqName(fqNameUnsafe: FqNameUnsafe): String =
        JavaToKotlinClassMap.mapKotlinToJava(fqNameUnsafe)?.asFqNameString() ?: fqNameUnsafe.asString()

    fun asConstraint() = when {
        kind == KaClassKind.OBJECT -> TypeConstraints.singleton(this)
        else -> TypeConstraints.exactClass(this)
    }

    companion object {
        context(KaSession)
        fun KaClassSymbol.classDef(): KtClassDef = KtClassDef(
            useSiteModule, classId?.hashCode() ?: name.hashCode(), createPointer(),
            classKind, modality
        )

        fun fromJvmClassName(context: KtElement, jvmClassName: String): KtClassDef? {
            val classId = ClassId.fromString(jvmClassName.replace("$", "."))
            val kotlinClassId = JavaToKotlinClassMap.mapJavaToKotlin(classId.asSingleFqName()) ?: classId
            return analyze(context) {
                findClass(kotlinClassId)?.classDef()
            }
        }

        fun typeConstraintFactory(context: KtElement): TypeConstraints.TypeConstraintFactory {
            return object : TypeConstraints.TypeConstraintFactory {
                override fun create(def: TypeConstraints.ClassDef): TypeConstraint.Exact = if (def !is KtClassDef) {
                    super.create(def)
                } else analyze(def.module) {
                    val symbol = def.pointer.restoreSymbol() ?: return@analyze TypeConstraints.unresolved(def.qualifiedName ?: "???")
                    var correctedDef = def
                    val classId = symbol.classId
                    if (classId != null) {
                        val correctedClassId = JavaToKotlinClassMap.mapJavaToKotlin(classId.asSingleFqName())
                        if (correctedClassId != null) {
                            val correctedSymbol = findClass(correctedClassId)
                            if (correctedSymbol != null) {
                                correctedDef = correctedSymbol.classDef()
                            }
                        }
                    }
                    correctedDef.asConstraint()
                }

                override fun create(fqn: String): TypeConstraint.Exact {
                    // We assume that supplied fqn is top-level
                    var fqName = FqName.fromSegments(fqn.split("."))
                    if (fqn.startsWith("java.")) {
                        fqName = JavaToKotlinClassMap.mapJavaToKotlin(fqName)?.asSingleFqName() ?: fqName
                    }
                    val name = fqName.toUnsafe()
                    return analyze(context) {
                        val symbol = findClass(ClassId.topLevel(name.toSafe()))
                        when {
                            symbol == null -> TypeConstraints.unresolved(name.asString())
                            symbol.classKind == KaClassKind.OBJECT -> TypeConstraints.singleton(symbol.classDef())
                            else -> TypeConstraints.exactClass(symbol.classDef())
                        }
                    }
                }
            }
        }
    }
}

