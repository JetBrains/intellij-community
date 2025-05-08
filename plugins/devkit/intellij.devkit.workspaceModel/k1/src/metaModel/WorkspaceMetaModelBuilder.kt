// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k1.metaModel

import com.intellij.devkit.workspaceModel.metaModel.IncorrectObjInterfaceException
import com.intellij.devkit.workspaceModel.metaModel.WorkspaceModelDefaults
import com.intellij.devkit.workspaceModel.metaModel.impl.*
import com.intellij.devkit.workspaceModel.metaModel.unsupportedType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.workspaceModel.codegen.deft.meta.*
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.testSourceInfo
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtensionProperty
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import java.util.concurrent.ConcurrentHashMap

internal class WorkspaceMetaModelBuilder(
  private val processAbstractTypes: Boolean,
  project: Project,
) {
  private val objModuleByName = ConcurrentHashMap<String, CompiledObjModuleAndK1Module>()
  private val javaPsiFacade = JavaPsiFacade.getInstance(project)
  private val allScope = GlobalSearchScope.allScope(project)

  fun getObjModule(packageName: String, module: Module, isTestSourceFolder: Boolean): CompiledObjModule {
    val sourceInfo = if (!isTestSourceFolder) {
      module.productionSourceInfo
    }
                     else {
      module.testSourceInfo
    } ?: error("No production sources in ${module.name}")
    val resolutionFacade = KotlinCacheService.getInstance(module.project).getResolutionFacadeByModuleInfo(sourceInfo, sourceInfo.platform)
    val moduleDescriptor = resolutionFacade.moduleDescriptor
    return getObjModule(packageName, moduleDescriptor)
  }

  private fun getObjModule(packageName: String, moduleDescriptor: ModuleDescriptor): CompiledObjModule {
    val cached = objModuleByName[packageName]
    if (cached != null && cached.kotlinModule == moduleDescriptor) return cached.compiledObjModule
    val packageViewDescriptor = moduleDescriptor.getPackage(FqName(packageName))
    val objModuleStub = createObjModuleStub(moduleDescriptor, packageViewDescriptor, packageName)
    val compiledObjModule = registerObjModuleContent(packageViewDescriptor, objModuleStub, moduleDescriptor)
    objModuleByName[packageName] = compiledObjModule and moduleDescriptor
    return compiledObjModule
  }

  private fun registerObjModuleContent(
    packageViewDescriptor: PackageViewDescriptor,
    objModuleStub: ObjModuleStub,
    moduleDescriptor: ModuleDescriptor,
  ): CompiledObjModule {
    val extensionProperties = packageViewDescriptor.fragments.flatMap { fragment ->
      fragment.getMemberScope().getContributedDescriptors(DescriptorKindFilter.VARIABLES)
        .filterIsInstance<PropertyDescriptor>()
        .filter { it.isExtensionProperty }
        .filter { it.module == moduleDescriptor }
    }
    val externalProperties = extensionProperties.mapNotNull { property ->
      val receiver = property.extensionReceiverParameter?.value?.type?.constructor?.declarationDescriptor as? ClassDescriptor
      receiver?.let { property to receiver }
    }.filter { it.second.isEntityInterface && !it.second.isEntityBuilderInterface }
    return objModuleStub.registerContent(externalProperties)
  }

  private fun createObjModuleStub(
    moduleDescriptor: ModuleDescriptor,
    packageViewDescriptor: PackageViewDescriptor, packageName: String,
  ): ObjModuleStub {
    val entityInterfaces = packageViewDescriptor.fragments.flatMap { fragment ->
      fragment.getMemberScope().getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS)
        .filterIsInstance<ClassDescriptor>()
        .filter { it.isEntityInterface }
        .filter { it.module == moduleDescriptor }
    }

    val module = CompiledObjModuleImpl(packageName)
    val types = entityInterfaces.sortedBy { it.name }.withIndex().map {
      it.value to createObjTypeStub(it.value, module)
    }

    return ObjModuleStub(module, types, moduleDescriptor.moduleAbstractTypes, moduleDescriptor)
  }

  private fun getObjClass(entityInterface: ClassDescriptor): ObjClass<*> {
    val containingPackage = entityInterface.packageOrDie
    val objModule = getObjModule(containingPackage.asString(), entityInterface.module)
    val entityInterfaceName = entityInterface.name.identifier
    return objModule.types.find { it.name == entityInterfaceName } ?: error("Cannot find $entityInterfaceName in $objModule")
  }

  private inner class ObjModuleStub(
    val compiledObjModule: CompiledObjModuleImpl,
    val types: List<Pair<ClassDescriptor, ObjClassImpl<Obj>>>,
    val moduleAbstractTypes: List<ClassDescriptor>,
    val moduleDescriptor: ModuleDescriptor,
  ) {
    fun registerContent(extProperties: List<Pair<PropertyDescriptor, ClassDescriptor>>): CompiledObjModule {
      var extPropertyId = 0
      for ((classDescriptor, objType) in types) {
        val properties = classDescriptor.unsubstitutedMemberScope.getContributedDescriptors()
          .filterIsInstance<PropertyDescriptor>()
          .filter { it.kind.isReal }
          .filter { it.module == moduleDescriptor }
        for ((propertyId, property) in properties.withIndex()) {
          val kind = property.computeKind()
          if (kind !is ObjProperty.ValueKind.Computable ||
              // We can't simply skip all `Computable` because some of them are SymbolicIds
              property.overriddenDescriptors.isNotEmpty()
          ) {
            objType.addField(createOwnProperty(property, propertyId, objType))
          }
        }
        classDescriptor.typeConstructor.supertypes.forEach { superType ->
          val superDescriptor = superType.constructor.declarationDescriptor
          if (superDescriptor is ClassDescriptor && superDescriptor.isEntityInterface) {
            val superClass = findObjClass(superDescriptor)
            objType.addSuperType(superClass)
          }
        }
        compiledObjModule.addType(objType)
      }

      moduleAbstractTypes.forEach { registerModuleAbstractType(it) }

      for ((extProperty, receiverClass) in extProperties) {
        compiledObjModule.addExtension(createExtProperty(extProperty, receiverClass, extPropertyId++))
      }
      return compiledObjModule
    }

    private fun registerModuleAbstractType(descriptor: ClassDescriptor) {
      val javaClassFqn = descriptor.javaClassFqn
      val superTypes = descriptor.superTypesJavaFqns

      val blobType = ValueType.Blob<Any>(javaClassFqn, superTypes)
      val inheritors = descriptor.inheritors(javaPsiFacade, allScope)
        .filter { it.packageName == compiledObjModule.name } // && it.module == moduleDescriptor }
        .map { classDescriptorToValueType(it, hashMapOf(javaClassFqn to blobType), true) }

      if (inheritors.isNotEmpty()) {
        compiledObjModule.addAbstractType(ValueType.AbstractClass<Any>(javaClassFqn, superTypes, inheritors))
      }
    }


    private fun createOwnProperty(
      property: PropertyDescriptor, propertyId: Int,
      receiver: ObjClassImpl<Obj>,
    ): OwnProperty<Obj, *> {
      val valueType = convertType(property.type, hashMapOf(), property.isAnnotatedBy(WorkspaceModelDefaults.CHILD_ANNOTATION.fqName))
      return OwnPropertyImpl(
        receiver, property.name.identifier, valueType, property.computeKind(),
        property.isAnnotatedBy(WorkspaceModelDefaults.OPEN_ANNOTATION.fqName), property.isVar,
        false, false, propertyId,
        property.isAnnotatedBy(WorkspaceModelDefaults.EQUALS_BY_ANNOTATION.fqName), property.source.getPsi()
      )
    }

    private fun createExtProperty(extProperty: PropertyDescriptor, receiverClass: ClassDescriptor, extPropertyId: Int): ExtProperty<*, *> {
      val valueType = convertType(extProperty.type, hashMapOf(), false)
      val propertyAnnotations = extProperty.getter?.annotations
                                  ?.mapNotNull { it.fqName }
                                  ?.map { ObjAnnotationImpl(it.asString(), it.pathSegments().map { segment -> segment.asString() }) }
                                ?: emptyList()
      return ExtPropertyImpl(
        findObjClass(receiverClass), extProperty.name.identifier, valueType,
        extProperty.computeKind(), extProperty.isAnnotatedBy(WorkspaceModelDefaults.OPEN_ANNOTATION.fqName),
        extProperty.isVar, false, compiledObjModule, extPropertyId, propertyAnnotations, extProperty.source.getPsi()
      )
    }


    private fun convertType(
      type: KotlinType,
      knownTypes: MutableMap<String, ValueType.Blob<*>>,
      hasChildAnnotation: Boolean,
    ): ValueType<*> {
      if (type.isMarkedNullable) {
        return ValueType.Optional(convertType(type.makeNotNullable(), knownTypes, hasChildAnnotation))
      }

      val descriptor = type.constructor.declarationDescriptor
      if (descriptor is ClassDescriptor) {
        val fqName = descriptor.fqNameSafe

        val primitive = ObjTypeConverter[fqName]
        if (primitive != null) return primitive

        if (fqName.isCollection) {
          val genericType = convertType(type.arguments.first().type, knownTypes, hasChildAnnotation)
          return when {
            fqName.isList -> ValueType.List(genericType)
            fqName.isSet -> ValueType.Set(genericType)
            fqName.isMap -> ValueType.Map(genericType, convertType(type.arguments.last().type, knownTypes, hasChildAnnotation))
            else -> unsupportedType(type.toString())
          }
        }

        if (descriptor.isEntityInterface) {
          return ValueType.ObjRef(type.isAnnotatedBy(
            WorkspaceModelDefaults.CHILD_ANNOTATION.fqName) || hasChildAnnotation, //todo leave only one target for @Child annotation
                                  findObjClass(descriptor))
        }
        return classDescriptorToValueType(descriptor, knownTypes, processAbstractTypes)
      }

      return unsupportedType(type.toString())
    }

    private fun classDescriptorToValueType(
      classDescriptor: ClassDescriptor,
      knownTypes: MutableMap<String, ValueType.Blob<*>>,
      processAbstractTypes: Boolean,
    ): ValueType.JvmClass<*> {
      val javaClassFqn = classDescriptor.javaClassFqn
      val superTypes = classDescriptor.superTypesJavaFqns

      val blobType = ValueType.Blob<Any>(javaClassFqn, superTypes)
      if (knownTypes.containsKey(javaClassFqn) || classDescriptor.isBlob) {
        return blobType
      }

      knownTypes[javaClassFqn] = blobType
      return when {
        classDescriptor.isObject -> ValueType.Object<Any>(javaClassFqn, superTypes, createProperties(classDescriptor, knownTypes))
        classDescriptor.isEnumClass -> {
          val values = classDescriptor.unsubstitutedInnerClassesScope.getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS)
            .filterIsInstance<ClassDescriptor>().filter { it.isEnumEntry }.map { it.name.asString() }.sorted()
          ValueType.Enum<Any>(javaClassFqn, superTypes, values, createProperties(classDescriptor, knownTypes).withoutEnumFields())
        }
        classDescriptor.isSealed() -> {
          val subclasses = classDescriptor.sealedSubclasses.map {
            convertType(it.defaultType, knownTypes, false) as ValueType.JvmClass<*>
          }
          ValueType.AbstractClass<Any>(javaClassFqn, superTypes, subclasses)
        }
        classDescriptor.isAbstractClassOrInterface -> {
          if (!processAbstractTypes) {
            throw IncorrectObjInterfaceException("$javaClassFqn is abstract type. Abstract types are not supported in generator")
          }
          val inheritors = classDescriptor.inheritors(javaPsiFacade, allScope)
            .filter { !it.isEntitySource || ( it.packageName == compiledObjModule.name) } // && it.module == moduleDescriptor) }
            .map { classDescriptorToValueType(it, knownTypes, processAbstractTypes) }
          ValueType.AbstractClass<Any>(javaClassFqn, superTypes, inheritors)
        }
        else -> ValueType.FinalClass<Any>(javaClassFqn, superTypes, createProperties(classDescriptor, knownTypes))
      }
    }

    private fun createProperties(
      descriptor: ClassDescriptor,
      knownTypes: MutableMap<String, ValueType.Blob<*>>,
    ): List<ValueType.ClassProperty<*>> {
      return descriptor.unsubstitutedMemberScope.getContributedDescriptors(DescriptorKindFilter.VARIABLES)
        .filterIsInstance<PropertyDescriptor>()
        .sortedBy { it.name.identifier }
        .map { ValueType.ClassProperty(it.name.identifier, convertType(it.type, knownTypes, false)) }
    }


    private fun findObjClass(descriptor: ClassDescriptor): ObjClass<*> {
      if (descriptor.packageOrDie.asString() == compiledObjModule.name) {
        return types.find { it.first.typeConstructor == descriptor.typeConstructor }?.second ?: error(
          "Cannot find ${descriptor.fqNameSafe} in $compiledObjModule")
      }
      return getObjClass(descriptor)
    }

    private fun Iterable<ValueType.ClassProperty<*>>.withoutEnumFields(): List<ValueType.ClassProperty<*>> {
      return filterNot { it.name == "name" || it.name == "ordinal" }
    }
  }
}
