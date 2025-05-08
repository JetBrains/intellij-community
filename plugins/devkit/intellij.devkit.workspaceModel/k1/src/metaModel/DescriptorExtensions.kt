// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k1.metaModel

import com.intellij.devkit.workspaceModel.metaModel.WorkspaceModelDefaults
import com.intellij.devkit.workspaceModel.metaModel.impl.CompiledObjModuleImpl
import com.intellij.devkit.workspaceModel.metaModel.impl.ObjAnnotationImpl
import com.intellij.devkit.workspaceModel.metaModel.impl.ObjClassImpl
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
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
  WorkspaceModelDefaults.ENTITY_SOURCE.classId,
  WorkspaceModelDefaults.SYMBOLIC_ENTITY_ID.classId
)

private val entitiesSuperclassFqn: FqName = WorkspaceModelDefaults.WORKSPACE_ENTITY.fqName

private val blobClasses: List<FqName> = arrayListOf(
  WorkspaceModelDefaults.VIRTUAL_FILE_URL.fqName,
  WorkspaceModelDefaults.ENTITY_POINTER.fqName
)

internal val ModuleDescriptor.moduleAbstractTypes: List<ClassDescriptor>
  get() = moduleAbstractTypesClassIds.mapNotNull { findClassAcrossModuleDependencies(it) }

internal fun ClassDescriptor.inheritors(
  javaPsiFacade: JavaPsiFacade, scope: GlobalSearchScope,
): List<ClassDescriptor> {
  val psiClass = javaPsiFacade.findClass(fqNameSafe.asString(), scope) ?: return emptyList()
  val inheritors = ClassInheritorsSearch.search(psiClass, scope, true, true, false)
    .filterNot { it.qualifiedName == null }
    .sortedBy { it.qualifiedName } // Sorting is needed for consistency in case of regeneration
  return inheritors.map { it.getJavaClassDescriptor()!! }
}

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
    .sorted()

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
  // If this is `WorkspaceEntity` interface, we need to check it
  get() = DescriptorUtils.isInterface(this) && defaultType.isSubclassOf(entitiesSuperclassFqn)

internal val ClassDescriptor.isEntitySource: Boolean
  get() = defaultType.isSubclassOf(WorkspaceModelDefaults.ENTITY_SOURCE.fqName)

internal val ClassDescriptor.isEntityBuilderInterface: Boolean
  get() = isEntityInterface && name.identifier == "Builder" // TODO: improve

private fun KotlinType.isSubclassOf(superClassName: FqName): Boolean {
  return constructor.declarationDescriptor?.fqNameSafe == superClassName || constructor.supertypes.any {
    it.constructor.declarationDescriptor?.fqNameSafe == superClassName || it.isSubclassOf(superClassName)
  }
}

internal fun createObjTypeStub(interfaceDescriptor: ClassDescriptor, module: CompiledObjModuleImpl): ObjClassImpl<Obj> {
  val openness = when {
    interfaceDescriptor.isAnnotatedBy(WorkspaceModelDefaults.ABSTRACT_ANNOTATION.fqName) -> ObjClass.Openness.abstract
    interfaceDescriptor.isAnnotatedBy(WorkspaceModelDefaults.OPEN_ANNOTATION.fqName) -> ObjClass.Openness.open
    else -> ObjClass.Openness.final
  }
  val propertyAnnotations = interfaceDescriptor.annotations
    .mapNotNull { it.fqName }
    .map { ObjAnnotationImpl(it.asString(), it.pathSegments().map { segment -> segment.asString() }) }
  return ObjClassImpl(module, interfaceDescriptor.name.identifier, openness, interfaceDescriptor.source.getPsi(), propertyAnnotations)
}

internal fun PropertyDescriptor.computeKind(): ObjProperty.ValueKind {
  val getter = getter ?: return ObjProperty.ValueKind.Plain
  if (!getter.hasBody()) return ObjProperty.ValueKind.Plain
  val declaration = getter.source.getPsi() as? KtDeclarationWithBody ?: return ObjProperty.ValueKind.Plain
  val getterText = (declaration.bodyExpression ?: declaration.bodyBlockExpression)?.text
  return when {
    getterText == null -> ObjProperty.ValueKind.Plain
    getter.isAnnotatedBy(WorkspaceModelDefaults.DEFAULT_ANNOTATION.fqName) -> ObjProperty.ValueKind.WithDefault(getterText)
    else -> ObjProperty.ValueKind.Computable(getterText)
  }
}

private val standardTypes = setOf(Any::class.qualifiedName, CommonClassNames.JAVA_LANG_OBJECT, CommonClassNames.JAVA_LANG_ENUM)

private fun Iterable<String>.withoutStandardTypes(): List<String> {
  return filterNot { standardTypes.contains(it) }
}
