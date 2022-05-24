// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.TypeConstraint
import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import org.jetbrains.kotlin.resolve.descriptorUtil.isSubclassOf
import org.jetbrains.kotlin.resolve.source.PsiSourceElement
import java.util.*
import java.util.stream.Stream
import kotlin.streams.asStream

internal class KtClassDef(val cls: ClassDescriptor, val context: KtElement) : TypeConstraints.ClassDef {
    override fun isInheritor(superClassQualifiedName: String): Boolean =
        cls.getAllSuperClassifiers().any { superClass ->
          superClass is ClassDescriptor && correctFqName(superClass.fqNameUnsafe) == superClassQualifiedName
        }

    override fun isInheritor(superType: TypeConstraints.ClassDef): Boolean =
      superType is KtClassDef && cls.isSubclassOf(superType.cls)

    override fun isConvertible(other: TypeConstraints.ClassDef): Boolean {
        if (other !is KtClassDef) return false
        // TODO: support sealed
        if (isInterface && other.isInterface) return true
        if (isInterface && !other.isFinal) return true
        if (other.isInterface && !isFinal) return true
        return isInheritor(other) || other.isInheritor(this)
    }

    override fun isInterface(): Boolean = cls.kind == ClassKind.INTERFACE

    override fun isEnum(): Boolean = cls.kind == ClassKind.ENUM_CLASS

    override fun isFinal(): Boolean = cls.modality == Modality.FINAL

    override fun isAbstract(): Boolean = cls.modality == Modality.ABSTRACT

    override fun getEnumConstant(ordinal: Int): PsiEnumConstant? {
        var psiClass = (cls.toSourceElement as? PsiSourceElement)?.psi
        if (psiClass is KtClass) {
            psiClass = psiClass.toLightClass()
        }
        if (psiClass is PsiClass) {
            var cur = 0
            for (field in psiClass.fields) {
                if (field is PsiEnumConstant) {
                    if (cur == ordinal) return field
                    cur++
                }
            }
        }
        return null
    }

    override fun getQualifiedName(): String = correctFqName(cls.fqNameUnsafe)

    override fun superTypes(): Stream<TypeConstraints.ClassDef> =
        cls.getAllSuperClassifiers().filterIsInstance<ClassDescriptor>().map { cd -> KtClassDef(cd, context) }.asStream()

    override fun toPsiType(project: Project): PsiType =
        JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(qualifiedName, context.resolveScope)

    override fun equals(other: Any?): Boolean {
        return other is KtClassDef && other.cls == cls
    }

    override fun hashCode(): Int = Objects.hashCode(cls.name.hashCode())

    override fun toString(): String = qualifiedName

    companion object {
        fun getClassConstraint(context: KtElement, name: FqNameUnsafe): TypeConstraint.Exact {
            val descriptor = context.findModuleDescriptor().resolveClassByFqName(name.toSafe(), NoLookupLocation.FROM_IDE)
            return if (descriptor == null) TypeConstraints.unresolved(name.asString())
            else TypeConstraints.exactClass(KtClassDef(descriptor, context))
        }

        fun typeConstraintFactory(context: KtElement): TypeConstraints.TypeConstraintFactory {
            return TypeConstraints.TypeConstraintFactory {
                    fqn -> getClassConstraint(context, FqName.fromSegments(fqn.split('.')).toUnsafe())
            }
        }
    }
}