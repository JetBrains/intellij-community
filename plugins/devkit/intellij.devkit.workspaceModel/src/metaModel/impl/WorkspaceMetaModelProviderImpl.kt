// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel.impl

import com.intellij.devkit.workspaceModel.metaModel.IncorrectObjInterfaceException
import com.intellij.devkit.workspaceModel.metaModel.WorkspaceMetaModelProvider
import com.intellij.devkit.workspaceModel.metaModel.impl.extensions.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.annotations.Open
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.workspaceModel.codegen.deft.meta.*
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.testSourceInfo
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtensionProperty
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import java.util.concurrent.ConcurrentHashMap

internal class WorkspaceMetaModelProviderImpl(
  private val processAbstractTypes: Boolean,
  project: Project
): WorkspaceMetaModelProvider {
  private val objModuleByName = ConcurrentHashMap<String, Pair<CompiledObjModule, ModuleDescriptor>>()
  private val javaPsiFacade = JavaPsiFacade.getInstance(project)
  private val allScope = GlobalSearchScope.allScope(project)

  override fun getObjModule(packageName: String, module: Module): CompiledObjModule {
    val sourceInfo = module.productionSourceInfo ?: module.testSourceInfo ?: error("No production sources in ${module.name}")
    val resolutionFacade = KotlinCacheService.getInstance(module.project).getResolutionFacadeByModuleInfo(sourceInfo, sourceInfo.platform)!!
    val moduleDescriptor = resolutionFacade.moduleDescriptor
    return getObjModule(packageName, moduleDescriptor)
  }

  private fun getObjModule(packageName: String, moduleDescriptor: ModuleDescriptor): CompiledObjModule {
    val cached = objModuleByName[packageName]
    if (cached != null && cached.second == moduleDescriptor) return cached.first
    val packageViewDescriptor = moduleDescriptor.getPackage(FqName(packageName))
    val objModuleStub = createObjModuleStub(moduleDescriptor, packageViewDescriptor, packageName)
    val result = registerObjModuleContent(packageViewDescriptor, objModuleStub)
    objModuleByName[packageName] = result to moduleDescriptor
    return result
  }

  private fun registerObjModuleContent(packageViewDescriptor: PackageViewDescriptor,
                                objModuleStub: ObjModuleStub): CompiledObjModule {
    val extensionProperties = packageViewDescriptor.fragments.flatMap { fragment ->
      fragment.getMemberScope().getContributedDescriptors(DescriptorKindFilter.VARIABLES)
        .filterIsInstance<PropertyDescriptor>()
        .filter { it.isExtensionProperty }
    }
    val externalProperties = extensionProperties.mapNotNull { property ->
          val receiver = property.extensionReceiverParameter?.value?.type?.constructor?.declarationDescriptor as? ClassDescriptor
          receiver?.let { property to receiver }
      }.filter { it.second.isEntityInterface && !it.second.isEntityBuilderInterface }
    return objModuleStub.registerContent(externalProperties)
  }

  private fun createObjModuleStub(moduleDescriptor: ModuleDescriptor,
                                  packageViewDescriptor: PackageViewDescriptor, packageName: String): ObjModuleStub {
    val entityInterfaces = packageViewDescriptor.fragments.flatMap { fragment ->
      fragment.getMemberScope().getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS)
        .filterIsInstance<ClassDescriptor>()
        .filter { it.isEntityInterface }
    }

    val module = CompiledObjModuleImpl(packageName)
    val types = entityInterfaces.sortedBy { it.name }.withIndex().map {
      it.value to createObjTypeStub(it.value, module)
    }

    return ObjModuleStub(module, types, moduleDescriptor.moduleAbstractTypes)
  }

  private fun getObjClass(entityInterface: ClassDescriptor): ObjClass<*> {
    val containingPackage = entityInterface.packageOrDie
    val objModule = getObjModule(containingPackage.asString(), entityInterface.module)
    val entityInterfaceName = entityInterface.name.identifier
    return objModule.types.find { it.name == entityInterfaceName } ?: error("Cannot find $entityInterfaceName in $objModule")
  }

  private inner class ObjModuleStub(val module: CompiledObjModuleImpl,
                                    val types: List<Pair<ClassDescriptor, ObjClassImpl<Obj>>>,
                                    val moduleAbstractTypes: List<ClassDescriptor>) {
    fun registerContent(extProperties: List<Pair<PropertyDescriptor, ClassDescriptor>>): CompiledObjModule {
      var extPropertyId = 0
      for ((classDescriptor, objType) in types) {
        val properties = classDescriptor.unsubstitutedMemberScope.getContributedDescriptors()
          .filterIsInstance<PropertyDescriptor>()
          .filter { it.kind.isReal }
        for ((propertyId, property) in properties.withIndex()) {
          val kind = computeKind(property)
          if (kind !is ObjProperty.ValueKind.Computable ||
              // We can't simply skip all `Computable` because some of them are SymbolicIds
              property.overriddenDescriptors.isNotEmpty()) {
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
        module.addType(objType)
      }

      moduleAbstractTypes.forEach { registerModuleAbstractType(it) }

      for ((extProperty, receiverClass) in extProperties) {
        module.addExtension(createExtProperty(extProperty, receiverClass, extPropertyId++))
      }
      return module
    }

    private fun registerModuleAbstractType(descriptor: ClassDescriptor) {
      val javaClassFqn = descriptor.javaClassFqn
      val superTypes = descriptor.superTypesJavaFqns

      val blobType = ValueType.Blob<Any>(javaClassFqn, superTypes)
      val inheritors = descriptor.inheritors(javaPsiFacade, allScope)
        .filter { it.packageName == module.name }
        .map { it.toValueType(hashMapOf(javaClassFqn to blobType), true) }

      if (inheritors.isNotEmpty()) {
        module.addAbstractType(ValueType.AbstractClass<Any>(javaClassFqn, superTypes, inheritors))
      }
    }


    private fun createOwnProperty(property: PropertyDescriptor, propertyId: Int,
                                  receiver: ObjClassImpl<Obj>): OwnProperty<Obj, *> {
      val valueType = convertType(property.type, hashMapOf(), property.isAnnotatedBy (StandardNames.CHILD_ANNOTATION))
      return OwnPropertyImpl(
        receiver, property.name.identifier, valueType, computeKind(property),
        property.isAnnotatedBy(StandardNames.OPEN_ANNOTATION), property.isVar,
        false, false, propertyId,
        property.isAnnotatedBy(StandardNames.EQUALS_BY_ANNOTATION), property.source
      )
    }

    private fun createExtProperty(extProperty: PropertyDescriptor, receiverClass: ClassDescriptor, extPropertyId: Int): ExtProperty<*, *> {
      val valueType = convertType(extProperty.type, hashMapOf(), false)
      return ExtPropertyImpl(
        findObjClass(receiverClass), extProperty.name.identifier, valueType,
        computeKind(extProperty), extProperty.isAnnotatedBy(StandardNames.OPEN_ANNOTATION),
        extProperty.isVar, false, module, extPropertyId, extProperty.source
      )
    }


    private fun convertType(type: KotlinType, knownTypes: MutableMap<String, ValueType.Blob<*>>, hasChildAnnotation: Boolean): ValueType<*> {
      if (type.isMarkedNullable) {
        return ValueType.Optional(convertType(type.makeNotNullable(), knownTypes, hasChildAnnotation))
      }

      val descriptor = type.constructor.declarationDescriptor
      if (descriptor is ClassDescriptor) {
        val fqName = descriptor.fqNameSafe

        val primitive = ObjTypeConverter.findPrimitive(fqName)
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
          return ValueType.ObjRef(type.isAnnotatedBy(StandardNames.CHILD_ANNOTATION) || hasChildAnnotation, //todo leave only one target for @Child annotation 
                                  findObjClass(descriptor))
        }
        return descriptor.toValueType(knownTypes, processAbstractTypes)
      }

      return unsupportedType(type.toString())
    }

    private fun ClassDescriptor.toValueType(knownTypes: MutableMap<String, ValueType.Blob<*>>, processAbstractTypes: Boolean): ValueType.JvmClass<*> {
      val javaClassFqn = javaClassFqn
      val superTypes = superTypesJavaFqns

      val blobType = ValueType.Blob<Any>(javaClassFqn, superTypes)
      if (knownTypes.containsKey(javaClassFqn) || isBlob) {
        return blobType
      }

      knownTypes[javaClassFqn] = blobType
      return when {
        isObject -> ValueType.Object<Any>(javaClassFqn, superTypes, createProperties(this, knownTypes))
        isEnumClass -> {
          val values = unsubstitutedInnerClassesScope.getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS)
            .filterIsInstance<ClassDescriptor>().filter { it.isEnumEntry }.map { it.name.asString() }
          ValueType.Enum<Any>(javaClassFqn, superTypes, values, createProperties(this, knownTypes).withoutEnumFields())
        }
        isSealed() -> {
          val subclasses = sealedSubclasses.map {
            convertType(it.defaultType, knownTypes, false) as ValueType.JvmClass<*>
          }
          ValueType.AbstractClass<Any>(javaClassFqn, superTypes, subclasses)
        }
        isAbstractClassOrInterface -> {
          if (!processAbstractTypes) {
            throw IncorrectObjInterfaceException("$javaClassFqn is abstract type. Abstract types are not supported in generator")
          }
          val inheritors = inheritors(javaPsiFacade, allScope)
            .map { it.toValueType(knownTypes, processAbstractTypes) }
          ValueType.AbstractClass<Any>(javaClassFqn, superTypes, inheritors)
        }
        else -> ValueType.FinalClass<Any>(javaClassFqn, superTypes, createProperties(this, knownTypes))
      }
    }

    private fun createProperties(descriptor: ClassDescriptor, knownTypes: MutableMap<String, ValueType.Blob<*>>): List<ValueType.ClassProperty<*>> {
      return descriptor.unsubstitutedMemberScope.getContributedDescriptors(DescriptorKindFilter.VARIABLES)
        .filterIsInstance<PropertyDescriptor>()
        .map { ValueType.ClassProperty(it.name.identifier, convertType(it.type, knownTypes, false)) }
    }


    private fun findObjClass(descriptor: ClassDescriptor): ObjClass<*> {
      if (descriptor.packageOrDie.asString() == module.name) {
        return types.find { it.first.typeConstructor == descriptor.typeConstructor }?.second ?: error("Cannot find ${descriptor.fqNameSafe} in $module")
      }
      return getObjClass(descriptor)
    }

    private fun Iterable<ValueType.ClassProperty<*>>.withoutEnumFields(): List<ValueType.ClassProperty<*>> {
      return filterNot { it.name == "name" || it.name == "ordinal" }
    }
  }
}


internal fun Annotated.isAnnotatedBy(fqName: FqName) = annotations.hasAnnotation(fqName)


private val FqName.isCollection: Boolean
  get() = isList || isSet || isMap

private val FqName.isList: Boolean
  get() = this == StandardNames.LIST_INTERFACE

private val FqName.isSet: Boolean
  get() = this == StandardNames.SET_INTERFACE

private val FqName.isMap: Boolean
  get() = this == StandardNames.MAP_INTERFACE


private object ObjTypeConverter {
  private val javaPrimitiveTypes = mapOf(
    CommonClassNames.JAVA_LANG_BOOLEAN to ValueType.Boolean,
    CommonClassNames.JAVA_LANG_BYTE to ValueType.Byte,
    CommonClassNames.JAVA_LANG_SHORT to ValueType.Short,
    CommonClassNames.JAVA_LANG_INTEGER to ValueType.Int,
    CommonClassNames.JAVA_LANG_LONG to ValueType.Long,
    CommonClassNames.JAVA_LANG_FLOAT to ValueType.Float,
    CommonClassNames.JAVA_LANG_DOUBLE to ValueType.Double,
    CommonClassNames.JAVA_LANG_CHARACTER to ValueType.Char,
    CommonClassNames.JAVA_LANG_STRING to ValueType.String,
  )

  private val kotlinPrimitiveTypes = mapOf(
    "kotlin.Boolean" to ValueType.Boolean,
    "kotlin.Byte" to ValueType.Byte,
    "kotlin.Short" to ValueType.Short,
    "kotlin.Int" to ValueType.Int,
    "kotlin.Long" to ValueType.Long,
    "kotlin.Float" to ValueType.Float,
    "kotlin.Double" to ValueType.Double,
    "kotlin.UByte" to ValueType.UByte,
    "kotlin.UShort" to ValueType.UShort,
    "kotlin.UInt" to ValueType.UInt,
    "kotlin.ULong" to ValueType.ULong,
    "kotlin.Char" to ValueType.Char,
    "kotlin.String" to ValueType.String,
  )

  fun findPrimitive(fqName: FqName): ValueType.Primitive<*>? =
    kotlinPrimitiveTypes[fqName.asString()] ?: javaPrimitiveTypes[fqName.asString()]
}

internal object StandardNames {
  val DEFAULT_ANNOTATION = FqName(Default::class.qualifiedName!!)
  val OPEN_ANNOTATION = FqName(Open::class.qualifiedName!!)
  val ABSTRACT_ANNOTATION = FqName(Abstract::class.qualifiedName!!)
  val CHILD_ANNOTATION = FqName(Child::class.qualifiedName!!)
  val EQUALS_BY_ANNOTATION = FqName(EqualsBy::class.qualifiedName!!)
  val LIST_INTERFACE = FqName(List::class.qualifiedName!!)
  val SET_INTERFACE = FqName(Set::class.qualifiedName!!)
  val MAP_INTERFACE = FqName(Map::class.qualifiedName!!)

  val WORKSPACE_ENTITY = FqName(WorkspaceEntity::class.qualifiedName!!)
  val ENTITY_SOURCE = FqName(EntitySource::class.qualifiedName!!)
  val VIRTUAL_FILE_URL = FqName(VirtualFileUrl::class.qualifiedName!!)
  val SYMBOLIC_ENTITY_ID = FqName(SymbolicEntityId::class.qualifiedName!!)
  val ENTITY_POINTER = FqName(EntityPointer::class.qualifiedName!!)
}

internal val standardTypes = setOf(Any::class.qualifiedName, CommonClassNames.JAVA_LANG_OBJECT, CommonClassNames.JAVA_LANG_ENUM)


private fun unsupportedType(type: String?): ValueType<*> {
  throw IncorrectObjInterfaceException("Unsupported type '$type'")
}