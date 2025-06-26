// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k2.metaModel

import com.intellij.devkit.workspaceModel.metaModel.WorkspaceEntityInheritsEntitySourceException
import com.intellij.devkit.workspaceModel.metaModel.IncorrectObjInterfaceException
import com.intellij.devkit.workspaceModel.metaModel.WorkspaceEntityMultipleInheritanceException
import com.intellij.devkit.workspaceModel.metaModel.WorkspaceModelDefaults
import com.intellij.devkit.workspaceModel.metaModel.impl.*
import com.intellij.devkit.workspaceModel.metaModel.unsupportedType
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.workspaceModel.codegen.deft.meta.*
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.stubindex.KotlinExactPackagesIndex
import org.jetbrains.kotlin.name.StandardClassIds
import java.util.concurrent.ConcurrentHashMap

internal class WorkspaceMetaModelBuilder(
  private val processAbstractTypes: Boolean,
  private val project: Project,
) {
  private val objModuleByName = ConcurrentHashMap<String, CompiledObjModuleAndK2Module>()
  private val javaPsiFacade = JavaPsiFacade.getInstance(project)
  private val allProjectScope = GlobalSearchScope.allScope(project)

  @RequiresReadLock
  fun getObjModule(
    packageName: String,
    kaModule: KaModule,
  ): CompiledObjModule = analyze(kaModule) {
    val ktFile = KotlinExactPackagesIndex.get(packageName, project, kaModule.contentScope).firstOrNull()
                 ?: error("Cannot find any files with package $packageName in module ${kaModule}")
    val packageSymbol = findPackage(ktFile.packageFqName) ?: error("Could not find package $packageName in module ${kaModule}")
    getObjModule(packageName, packageSymbol, kaModule)
  }

  private fun KaSession.getObjModule(
    packageName: String,
    packageSymbol: KaPackageSymbol,
    kaModule: KaModule,
  ): CompiledObjModule {
    val cached = objModuleByName[packageName]
    if (cached != null && cached.kotlinModule == kaModule) return cached.compiledObjModule
    val objModuleStub = createObjModuleStub(packageName, packageSymbol, kaModule)
    val compiledObjModule = registerObjModuleContent(packageSymbol, objModuleStub, kaModule)
    objModuleByName[packageName] = compiledObjModule and kaModule
    return compiledObjModule
  }

  private fun KaSession.registerObjModuleContent(
    packageSymbol: KaPackageSymbol,
    objModuleStub: ObjModuleStub,
    kaModule: KaModule,
  ): CompiledObjModule {
    val extensionProperties = packageSymbol.packageScope.callables
      .filterIsInstance<KaPropertySymbol>()
      .filter { it.isExtension }
      .filter { it.containingModule == kaModule }
      .mapNotNull { propertySymbol ->
        propertySymbol.receiverType?.expandedSymbol
          ?.takeIf { receiverClassSymbol -> isEntityInterface(receiverClassSymbol) && !isEntityBuilderInterface(receiverClassSymbol) }
          ?.let { propertySymbol to it }
      }
    return objModuleStub.registerContent(this@registerObjModuleContent, extensionProperties.toList())
  }

  private fun KaSession.createObjModuleStub(
    packageName: String,
    packageSymbol: KaPackageSymbol,
    kaModule: KaModule,
  ): ObjModuleStub {
    val module = CompiledObjModuleImpl(packageName)

    val entityTypes = packageSymbol.packageScope.declarations
      .filterIsInstance<KaClassSymbol>()
      .filter(::isEntityInterface)
      .filter { it.containingModule == kaModule }
      .sortedBy { it.name }
      .map { it to createObjTypeStub(it, module) }
      .toList()

    val moduleAbstractTypes = moduleAbstractTypes()
    return ObjModuleStub(module, entityTypes, moduleAbstractTypes, kaModule)
  }

  private fun KaSession.getObjClass(entityInterface: KaClassSymbol): ObjClass<*> {
    val containingPackage = getPackageSymbol(entityInterface) ?: error("Cannot find package for ${entityInterface.name ?: entityInterface}")
    val objModule = getObjModule(entityInterface.packageName, containingPackage, entityInterface.containingModule)
    val entityInterfaceName = entityInterface.name?.identifier ?: error("Too many errors")
    return objModule.types.find { it.name == entityInterfaceName } ?: error("Cannot find $entityInterfaceName in $objModule")
  }

  private inner class ObjModuleStub(
    val compiledObjModule: CompiledObjModuleImpl,
    // TODO: storing KaClassSymbol is unsafe, ObjModuleStub should inherit from KaLifetimeOwner
    val entityTypes: List<Pair<KaClassSymbol, ObjClassImpl<Obj>>>,
    val moduleAbstractTypes: List<KaClassSymbol>,
    val kaModule: KaModule,
  ) /* : KaLifetimeOwner */ {
    @RequiresReadLock
    fun registerContent(kaSession: KaSession, extProperties: List<Pair<KaPropertySymbol, KaClassSymbol>>): CompiledObjModule =
      with(kaSession) {
        var extPropertyId = 0
        for ((classSymbol, objType) in entityTypes) {
          val properties = classSymbol.declaredMemberScope.callables
            .filterIsInstance<KaPropertySymbol>().toList()
            .filter { it.containingModule == kaModule }

          for ((propertyId, propertySymbol) in properties.withIndex()) {
            val kind = computeKind(propertySymbol)
            if (kind !is ObjProperty.ValueKind.Computable ||
                // We can't simply skip all `Computable` because some of them are SymbolicIds
                // propertySymbol.overriddenDescriptors.isNotEmpty()
                propertySymbol.allOverriddenSymbols.any()
            ) {
              objType.addField(createOwnProperty(propertySymbol, propertyId, objType))
            }
          }

          val extendedAbstract = mutableSetOf<String>()
          classSymbol.superTypes
            .map { it.expandedSymbol }
            .filterIsInstance<KaClassSymbol>()
            .forEach { superSymbol ->
              if (isEntityInterface(superSymbol)) {
                val superClass = findObjClass(superSymbol)
                objType.addSuperType(superClass)
                extendedAbstract.add(superClass.name)
              }
              if (isEntitySource(superSymbol)) {
                throw WorkspaceEntityInheritsEntitySourceException(classSymbol.javaClassFqn)
              }
            }
          if (extendedAbstract.size > 1) {
            throw WorkspaceEntityMultipleInheritanceException(classSymbol.javaClassFqn, extendedAbstract)
          }

          compiledObjModule.addType(objType)
        }

        registerModuleAbstractTypes()

        for ((extProperty, receiverClass) in extProperties) {
          compiledObjModule.addExtension(createExtProperty(extProperty, receiverClass, extPropertyId++))
        }

        compiledObjModule
      }

    private fun KaSession.registerModuleAbstractTypes() {
      for (abstractTypeClassSymbol in moduleAbstractTypes) {
        val javaClassFqn = abstractTypeClassSymbol.javaClassFqn
        val superTypes = abstractTypeClassSymbol.superTypesJavaFqns
        val blobType = ValueType.Blob<Any>(javaClassFqn, superTypes)

        val inheritors = mutableListOf<ValueType.JvmClass<*>>()
        val inheritorsKtClasses = inheritors(abstractTypeClassSymbol, javaPsiFacade, allProjectScope)
        for (inheritor in inheritorsKtClasses) {
          analyze(inheritor) {
            val inheritorSymbol = inheritor.namedClassSymbol
            // FIXME: Check for module removed because of problems with kotlin.base.scripting in tests, might liead to other problems
            if (inheritorSymbol != null && inheritorSymbol.packageName == compiledObjModule.name) { // && inheritorSymbol.containingModule == this@ObjModuleStub.kaModule ) {
              val inheritorValueType = classSymbolToValueType(inheritorSymbol, hashMapOf(javaClassFqn to blobType), true)
              inheritors.add(inheritorValueType)
            }
          }
        }

        if (inheritors.isNotEmpty()) {
          compiledObjModule.addAbstractType(ValueType.AbstractClass<Any>(javaClassFqn, superTypes, inheritors))
        }
      }
    }

    private fun KaSession.createOwnProperty(
      property: KaPropertySymbol,
      propertyId: Int,
      receiver: ObjClassImpl<Obj>,
    ): OwnProperty<Obj, *> {
      val hasParentAnnotation = isParent(property)
      val valueType = convertType(property.returnType, hashMapOf(), hasParentAnnotation)
      return OwnPropertyImpl(receiver, property.name.identifier, valueType, computeKind(property),
                             property.isAnnotatedBy(WorkspaceModelDefaults.OPEN_ANNOTATION.classId), !property.isVal, false, false,
                             propertyId, property.isAnnotatedBy(WorkspaceModelDefaults.EQUALS_BY_ANNOTATION.classId),
                             property.sourcePsiSafe())
    }

    private fun KaSession.createExtProperty(
      extProperty: KaPropertySymbol,
      receiverClass: KaClassSymbol,
      extPropertyId: Int,
    ): ExtProperty<*, *> {
      val hasParentAnnotation = isParent(extProperty)
      val valueType = convertType(extProperty.returnType, hashMapOf(), hasParentAnnotation)
      val propertyAnnotations = extProperty.getter?.annotations?.mapNotNull { annotation ->
        annotation.classId?.asSingleFqName()?.let {
          ObjAnnotationImpl(it.asString(), it.pathSegments().map { segment -> segment.asString() })
        }
      } ?: emptyList()

      return ExtPropertyImpl(findObjClass(receiverClass), extProperty.name.identifier, valueType, computeKind(extProperty),
                             extProperty.isAnnotatedBy(WorkspaceModelDefaults.OPEN_ANNOTATION.classId), !extProperty.isVal, false, compiledObjModule,
                             extPropertyId, propertyAnnotations, extProperty.sourcePsiSafe())
    }

    private fun KaSession.convertType(
      type: KaType,
      knownTypes: MutableMap<String, ValueType.Blob<*>>,
      hasParentAnnotation: Boolean,
    ): ValueType<*> {
      if (type !is KaClassType) error("$type is not a class")
      if (type.nullability == KaTypeNullability.NULLABLE) {
        val nonNullableType = type.withNullability(KaTypeNullability.NON_NULLABLE)
        return ValueType.Optional(convertType(nonNullableType, knownTypes, hasParentAnnotation))
      }

      val symbol = type.expandedSymbol
      if (symbol is KaClassSymbol) {
        val kotlinDefaultValueType = ObjTypeConverter[type.symbol.classId]
        if (kotlinDefaultValueType != null) return kotlinDefaultValueType

        if (type.isSubtypeOf(StandardClassIds.Collection)) {
          val typeArguments = type.typeArguments.mapNotNull { it.type }
          val genericType = convertType(typeArguments.first(), knownTypes, hasParentAnnotation)
          when {
            type.isSubtypeOf(StandardClassIds.List) -> return ValueType.List(genericType)
            type.isSubtypeOf(StandardClassIds.Set) -> return ValueType.Set(genericType)
            else -> return unsupportedType(type.toString())
          }
        }

        if (type.isSubtypeOf(StandardClassIds.Map)) {
          val (keyType, valueType) = type.typeArguments.mapNotNull { it.type }
          val convertedKeyType = convertType(keyType, knownTypes, hasParentAnnotation)
          val convertedValueType = convertType(valueType, knownTypes, hasParentAnnotation)
          return ValueType.Map(convertedKeyType, convertedValueType)
        }

        if (symbol.classKind == KaClassKind.INTERFACE && type.isSubtypeOf(WorkspaceModelDefaults.WORKSPACE_ENTITY.classId)) {
          return ValueType.ObjRef(!hasParentAnnotation, findObjClass(symbol))
        }

        return classSymbolToValueType(symbol, knownTypes, processAbstractTypes)
      }

      return unsupportedType(type.toString())
    }

    private fun KaSession.findObjClass(classSymbol: KaClassSymbol): ObjClass<*> {
      if (classSymbol.packageOrDie.asString() == compiledObjModule.name) {
        return entityTypes.find { it.first.classId == classSymbol.classId }?.second
               ?: error("Cannot find ${classSymbol.name} in $compiledObjModule")
      }
      return getObjClass(classSymbol)
    }

    private fun KaSession.classSymbolToValueType(
      classSymbol: KaClassSymbol,
      knownTypes: MutableMap<String, ValueType.Blob<*>>,
      processAbstractTypes: Boolean,
    ): ValueType.JvmClass<*> {
      val javaClassFqn = classSymbol.javaClassFqn
      val superTypesJavaFqns = superTypesJavaFqns(classSymbol, javaPsiFacade, allProjectScope)

      val blobType = ValueType.Blob<Any>(javaClassFqn, superTypesJavaFqns)
      if (knownTypes.containsKey(javaClassFqn) || isBlob(classSymbol)) {
        return blobType
      }

      knownTypes[javaClassFqn] = blobType

      return when {
        classSymbol.classKind == KaClassKind.OBJECT -> ValueType.Object<Any>(javaClassFqn, superTypesJavaFqns,
                                                                             createProperties(classSymbol, knownTypes).toList())
        classSymbol.classKind == KaClassKind.ENUM_CLASS -> {
          val enumEntries =
            classSymbol.staticMemberScope.callables.filterIsInstance<KaEnumEntrySymbol>().map { it.name.asString() }.sorted()
          ValueType.Enum<Any>(javaClassFqn, superTypesJavaFqns, enumEntries.toList(),
                              createProperties(classSymbol, knownTypes).withoutEnumFields().toList())
        }
        classSymbol.modality == KaSymbolModality.SEALED && classSymbol is KaNamedClassSymbol -> {
          val subclasses = classSymbol.sealedClassInheritors
            .map { convertType(it.defaultType, knownTypes, false) as ValueType.JvmClass<*> }
          ValueType.AbstractClass<Any>(javaClassFqn, superTypesJavaFqns, subclasses)
        }
        classSymbol.classKind == KaClassKind.INTERFACE || classSymbol.modality != KaSymbolModality.FINAL -> {
          if (!processAbstractTypes) {
            throw IncorrectObjInterfaceException("$javaClassFqn is abstract type. Abstract types are not supported in generator")
          }
          val inheritors = inheritors(classSymbol, javaPsiFacade, allProjectScope)
            .mapNotNull { ktClassOrObject ->
              analyze(ktClassOrObject) {
                val inheritorSymbol = ktClassOrObject.namedClassSymbol
                if (inheritorSymbol != null && (!isEntitySource(inheritorSymbol) || inheritorSymbol.packageName == compiledObjModule.name)) {
                  classSymbolToValueType(inheritorSymbol, knownTypes, processAbstractTypes)
                }
                else null
              }
            }
          ValueType.AbstractClass<Any>(javaClassFqn, superTypesJavaFqns, inheritors)
        }
        else -> ValueType.FinalClass<Any>(javaClassFqn, superTypesJavaFqns, createProperties(classSymbol, knownTypes).toList())
      }
    }

    private fun KaSession.createProperties(classSymbol: KaClassSymbol, knownTypes: MutableMap<String, ValueType.Blob<*>>) =
      classSymbol.memberScope.callables
        .filterIsInstance<KaPropertySymbol>()
        .sortedBy { symbol -> symbol.name }
        .map { propertySymbol ->
          propertySymbol.location
          ValueType.ClassProperty(propertySymbol.name.identifier, convertType(propertySymbol.returnType, knownTypes, false))
        }
  }

  // TODO: Kirill does not understand why it exists
  private fun Sequence<ValueType.ClassProperty<*>>.withoutEnumFields() = filterNot { it.name == "name" || it.name == "ordinal" }
}
