// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k2.metaModel

import com.intellij.devkit.workspaceModel.metaModel.WorkspaceModelDefaults
import com.intellij.devkit.workspaceModel.metaModel.impl.CompiledObjModuleImpl
import com.intellij.devkit.workspaceModel.metaModel.impl.ObjAnnotationImpl
import com.intellij.devkit.workspaceModel.metaModel.impl.ObjClassImpl
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.workspaceModel.codegen.deft.meta.Obj
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclarationWithBody

internal fun KaAnnotated.isAnnotatedBy(classId: ClassId) = annotations.contains(classId)

private val moduleAbstractTypesClassIds: List<ClassId> = listOf(
  WorkspaceModelDefaults.ENTITY_SOURCE.classId,
  WorkspaceModelDefaults.SYMBOLIC_ENTITY_ID.classId,
)

internal fun KaSession.inheritors(
  classSymbol: KaClassSymbol, javaPsiFacade: JavaPsiFacade, scope: GlobalSearchScope,
): List<KtClassOrObject> {
  // can be rewritten using org.jetbrains.kotlin.idea.searching.inheritors.KotlinSearchUtilKt.findAllInheritors
  val psiClass = javaPsiFacade.findClass(classSymbol.javaClassFqn, scope) ?: return emptyList()
  return ClassInheritorsSearch.search(psiClass, scope, true, true, false)
    .filterNot { it.isAnonymous }
    .sortedBy { it.qualifiedName } // Sorting is needed for consistency in case of regeneration
    .mapNotNull {
      KotlinFullClassNameIndex[it.qualifiedName!!, it.project, scope].firstOrNull()
    }
}

private val standardTypes = setOf(Any::class.qualifiedName, CommonClassNames.JAVA_LANG_OBJECT, CommonClassNames.JAVA_LANG_ENUM)

internal fun KaSession.superTypesJavaFqns(
  classSymbol: KaClassSymbol, javaPsiFacade: JavaPsiFacade, scope: GlobalSearchScope,
): List<String> {
  // FIXME: There's definitely smth wrong with AA
  // FIXME: replace mapNotNull + superTypesFqns.addAll with mapNotNullTo(superTypesFqns)
  val superTypesFqns: MutableSet<String> = mutableSetOf()
  val kaSuperTypes1 = classSymbol.defaultType.allSupertypes
    .mapNotNull { it.expandedSymbol?.javaClassFqn }
  superTypesFqns.addAll(kaSuperTypes1)
  val kaSuperTypes2 = classSymbol.superTypes.flatMap { listOf(it) + it.allSupertypes }
    .mapNotNull { it.expandedSymbol?.javaClassFqn }
  superTypesFqns.addAll(kaSuperTypes2)
  val psiClass = javaPsiFacade.findClass(classSymbol.javaClassFqn, scope)
  if (psiClass != null) {
    val psiSuperTypes = psiClass.supers.mapNotNull { it.qualifiedName }
    superTypesFqns.addAll(psiSuperTypes)
  }
  return superTypesFqns.withoutStandardTypes().sorted()
}

// TODO: merge with above?
internal val KaClassSymbol.superTypesJavaFqns: List<String>
  get() = superTypes.mapNotNull { it.symbol?.javaClassFqn }.withoutStandardTypes().sorted()

private val PsiClass.isAnonymous: Boolean
  get() = qualifiedName == null

internal val KaClassLikeSymbol.javaClassFqn: String
  get() {
    val nameWithoutPackage = classId?.asSingleFqName()?.asString()
      ?.substringAfter("$packageName.")
      ?.replace(".", "$")
    return "$packageName.$nameWithoutPackage"
  }

internal val KaClassLikeSymbol.packageName: String
  get() = packageOrDie.asString()

internal val KaClassLikeSymbol.packageOrDie: FqName
  get() = classId?.packageFqName ?: error("$name has no package")

internal fun KaSession.getPackageSymbol(classSymbol: KaClassSymbol): KaPackageSymbol? =
  classSymbol.classId?.packageFqName?.let { findPackage(it) }

private val blobClasses: List<ClassId> = arrayListOf(
  WorkspaceModelDefaults.VIRTUAL_FILE_URL.classId,
  WorkspaceModelDefaults.ENTITY_POINTER.classId,
)

internal fun KaSession.isBlob(classSymbol: KaClassSymbol): Boolean {
  val classId = classSymbol.classId ?: return false
  return blobClasses.any { blobClass -> classSymbol.defaultType.isSubtypeOf(blobClass) } ||
         classId in moduleAbstractTypesClassIds ||
         classId.asSingleFqName().asString().startsWith("java.") ||
         classId.asSingleFqName().asString().startsWith("kotlin.")
}

private fun Iterable<String>.withoutStandardTypes(): List<String> {
  return filterNot { standardTypes.contains(it) }
}

internal fun KaSession.moduleAbstractTypes(): List<KaClassSymbol> {
  return moduleAbstractTypesClassIds.mapNotNull { classId -> findClass(classId) }
}

internal fun KaSession.isEntityInterface(classSymbol: KaClassSymbol): Boolean =
  classSymbol.classKind == KaClassKind.INTERFACE && classSymbol.defaultType.isSubtypeOf(WorkspaceModelDefaults.WORKSPACE_ENTITY.classId)

internal fun KaSession.isEntityBuilderInterface(classSymbol: KaClassSymbol): Boolean =
  isEntityInterface(classSymbol) && classSymbol.name?.identifier == "Builder" // TODO: improve

internal fun KaSession.isEntitySource(classSymbol: KaClassSymbol): Boolean =
  classSymbol.defaultType.isSubtypeOf(WorkspaceModelDefaults.ENTITY_SOURCE.classId)

internal fun KaSession.computeKind(property: KaPropertySymbol): ObjProperty.ValueKind {
  val getter = property.getter ?: return ObjProperty.ValueKind.Plain
  if (!getter.hasBody) return ObjProperty.ValueKind.Plain
  val declaration = getter.psi as? KtDeclarationWithBody ?: return ObjProperty.ValueKind.Plain
  val getterText = (declaration.bodyExpression ?: declaration.bodyBlockExpression)?.text
  return when {
    getterText == null -> ObjProperty.ValueKind.Plain
    getter.isAnnotatedBy(WorkspaceModelDefaults.DEFAULT_ANNOTATION.classId) -> ObjProperty.ValueKind.WithDefault(getterText)
    else -> ObjProperty.ValueKind.Computable(getterText)
  }
}

internal fun KaSession.createObjTypeStub(symbol: KaClassSymbol, module: CompiledObjModuleImpl): ObjClassImpl<Obj> {
  val openness = when {
    symbol.isAnnotatedBy(WorkspaceModelDefaults.ABSTRACT_ANNOTATION.classId) -> ObjClass.Openness.abstract
    symbol.isAnnotatedBy(WorkspaceModelDefaults.OPEN_ANNOTATION.classId) -> ObjClass.Openness.open
    else -> ObjClass.Openness.final
  }

  val propertyAnnotations = symbol.annotations
    .mapNotNull { it.classId?.asSingleFqName() }
    .map { ObjAnnotationImpl(it.asString(), it.pathSegments().map { segment -> segment.asString() }) }

  return ObjClassImpl(module, symbol.name?.identifier!!, openness, symbol.sourcePsi(), propertyAnnotations)
}

internal fun KaSession.isChild(kaType: KaAnnotated) =
  kaType.isAnnotatedBy(WorkspaceModelDefaults.CHILD_ANNOTATION.classId)
