// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel.impl.extensions

import com.intellij.devkit.workspaceModel.metaModel.impl.*
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.workspaceModel.codegen.deft.meta.Obj
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes


private val moduleAbstractTypesClassIds: List<ClassId> = listOf(
  ClassId.topLevel(StandardNames.ENTITY_SOURCE),
  ClassId.topLevel(StandardNames.SYMBOLIC_ENTITY_ID)
)

private val entitiesSuperclassFqn = StandardNames.WORKSPACE_ENTITY

internal val blobClasses: List<FqName> = arrayListOf(
  StandardNames.VIRTUAL_FILE_URL,
  StandardNames.ENTITY_POINTER
)


internal val ModuleDescriptor.moduleAbstractTypes: List<ClassDescriptor>
  get() = moduleAbstractTypesClassIds.mapNotNull { findClassAcrossModuleDependencies(it) }

internal fun ClassDescriptor.inheritors(
  javaPsiFacade: JavaPsiFacade, scope: GlobalSearchScope
): List<ClassDescriptor> {
  val psiClass = javaPsiFacade.findClass(fqNameSafe.asString(), scope) ?: return emptyList()
  return psiClass.inheritors(scope).map { it.getJavaClassDescriptor()!! }
}

private fun PsiClass.inheritors(scope: SearchScope): List<PsiClass> {
  val inheritors = ClassInheritorsSearch.search(this, scope, true, true, false).filterNot { it.isAnonymous }
  return inheritors.sortedBy { it.qualifiedName } //Sorting is needed for consistency in case of regeneration
}

private val PsiClass.isAnonymous: Boolean
  get() = qualifiedName == null


internal val ClassifierDescriptor.javaClassFqn: String
  get() {
    val nameWithoutPackage = fqNameSafe.asString()
      .substringAfter("$packageName.")
      .replace(".", "$")
    return "$packageName.$nameWithoutPackage"
  }

internal val ClassifierDescriptor.packageName: String
  get() = packageOrDie.asString()

internal val ClassifierDescriptor.packageOrDie: FqName
  get() = containingPackage() ?: error("${fqNameUnsafe.asString()} has no package")

internal val ClassDescriptor.superTypesJavaFqns: List<String>
  get() = defaultType.supertypes()
    .mapNotNull { it.constructor.declarationDescriptor?.javaClassFqn }
    .withoutStandardTypes()


internal val ClassDescriptor.isObject: Boolean
  get() = kind == ClassKind.OBJECT

internal val ClassDescriptor.isEnumEntry: Boolean
  get() = kind == ClassKind.ENUM_ENTRY

internal val ClassDescriptor.isEnumClass: Boolean
  get() = kind == ClassKind.ENUM_CLASS

internal val ClassDescriptor.isBlob: Boolean
  get() {
    val fqName = fqNameSafe.asString()
    return blobClasses.any { defaultType.isSubclassOf(it) } ||
           moduleAbstractTypesClassIds.any { fqName == it.asFqNameString() } ||
           fqName.startsWith("java.") || fqName.startsWith("kotlin.")
  }

internal val ClassDescriptor.isAbstractClassOrInterface: Boolean
  get() = !isFinalClass

internal val ClassDescriptor.isEntityInterface: Boolean
  get() = DescriptorUtils.isInterface(this) && defaultType.isSubclassOf(entitiesSuperclassFqn) // If this is `WorkspaceEntity` interface we need to check it

internal val ClassDescriptor.isEntityBuilderInterface: Boolean
  get() = isEntityInterface && name.identifier == "Builder" //todo improve


private fun KotlinType.isSubclassOf(superClassName: FqName): Boolean {
  return constructor.declarationDescriptor?.fqNameSafe == superClassName || constructor.supertypes.any {
    it.constructor.declarationDescriptor?.fqNameSafe == superClassName || it.isSubclassOf(superClassName)
  }
}


internal fun createObjTypeStub(interfaceDescriptor: ClassDescriptor, module: CompiledObjModuleImpl): ObjClassImpl<Obj> {
  val openness = when {
    interfaceDescriptor.isAnnotatedBy(StandardNames.ABSTRACT_ANNOTATION) -> ObjClass.Openness.abstract
    interfaceDescriptor.isAnnotatedBy(StandardNames.OPEN_ANNOTATION) -> ObjClass.Openness.open
    else -> ObjClass.Openness.final
  }
  return ObjClassImpl(module, interfaceDescriptor.name.identifier, openness, interfaceDescriptor.source)
}

internal fun computeKind(property: PropertyDescriptor): ObjProperty.ValueKind {
  val getter = property.getter ?: return ObjProperty.ValueKind.Plain
  if (!getter.hasBody()) return ObjProperty.ValueKind.Plain
  val declaration = getter.source.getPsi() as? KtDeclarationWithBody ?: return ObjProperty.ValueKind.Plain
  val getterText = (declaration.bodyExpression ?: declaration.bodyBlockExpression)?.text
  return when {
    getterText == null -> ObjProperty.ValueKind.Plain
    getter.isAnnotatedBy(StandardNames.DEFAULT_ANNOTATION) -> ObjProperty.ValueKind.WithDefault(getterText)
    else -> ObjProperty.ValueKind.Computable(getterText)
  }
}


private fun Iterable<String>.withoutStandardTypes(): List<String> {
  return filterNot { standardTypes.contains(it) }
}