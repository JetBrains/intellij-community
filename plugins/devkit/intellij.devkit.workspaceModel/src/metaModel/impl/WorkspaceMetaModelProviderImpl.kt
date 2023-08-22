// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel.impl

import com.intellij.devkit.workspaceModel.metaModel.IncorrectObjInterfaceException
import com.intellij.devkit.workspaceModel.metaModel.WorkspaceMetaModelProvider
import com.intellij.openapi.module.Module
import com.intellij.workspaceModel.codegen.deft.meta.*
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.EqualsBy
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.annotations.Open
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.testSourceInfo
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.resolve.DescriptorUtils.isInterface
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtensionProperty
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import org.jetbrains.kotlin.types.typeUtil.supertypes
import java.util.concurrent.ConcurrentHashMap

class WorkspaceMetaModelProviderImpl : WorkspaceMetaModelProvider {
  private val objModuleByName = ConcurrentHashMap<String, Pair<CompiledObjModule, ModuleDescriptor>>()

  private fun getObjClass(entityInterface: ClassDescriptor): ObjClass<*> {
    val containingPackage = entityInterface.containingPackage() ?: error("${entityInterface.fqNameUnsafe.asString()} has no package")
    val objModule = getObjModule(containingPackage.asString(), entityInterface.module)
    val entityInterfaceName = entityInterface.name.identifier
    return objModule.types.find { it.name == entityInterfaceName } ?: error("Cannot find $entityInterfaceName in $objModule")
  }

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
    val objModuleStub = createObjModuleStub(packageViewDescriptor, packageName)
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
    val externalProperties =
      extensionProperties
        .mapNotNull { property ->
          val receiver = property.extensionReceiverParameter?.value?.type?.constructor?.declarationDescriptor as? ClassDescriptor
          receiver?.let { property to receiver }
        }
        .filter { it.second.isEntityInterface && !it.second.isEntityBuilderInterface }
    return objModuleStub.registerContent(externalProperties)
  }

  private fun createObjModuleStub(packageViewDescriptor: PackageViewDescriptor,
                                  packageName: String): ObjModuleStub {
    val entityInterfaces = packageViewDescriptor.fragments.flatMap { fragment ->
      fragment.getMemberScope().getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS)
        .filterIsInstance<ClassDescriptor>()
        .filter { it.isEntityInterface }
    }
    val module = CompiledObjModuleImpl(packageName)
    val types = entityInterfaces.sortedBy { it.name }.withIndex().map {
      it.value to createObjTypeStub(it.value, module)
    }
    return ObjModuleStub(module, types)
  }

  private inner class ObjModuleStub(val module: CompiledObjModuleImpl, val types: List<Pair<ClassDescriptor, ObjClassImpl<Obj>>>) {
    fun registerContent(extProperties: List<Pair<PropertyDescriptor, ClassDescriptor>>): CompiledObjModule {
      var extPropertyId = 0
      for ((classDescriptor, objType) in types) {
        val properties = classDescriptor.unsubstitutedMemberScope.getContributedDescriptors()
          .filterIsInstance<PropertyDescriptor>()
          .filter { it.kind.isReal }
        for ((propertyId, property) in properties.withIndex()) {
          objType.addField(createOwnProperty(property, propertyId, classDescriptor, objType))
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
      for ((extProperty, receiverClass) in extProperties) {
        module.addExtension(createExtProperty(extProperty, receiverClass, extPropertyId++))
      }
      return module
    }

    private fun createExtProperty(extProperty: PropertyDescriptor, receiverClass: ClassDescriptor, extPropertyId: Int): ExtProperty<*, *> {
      val valueType = convertType(extProperty.type, "${receiverClass.fqNameSafe.asString()}::${extProperty.name}")
      return ExtPropertyImpl(findObjClass(receiverClass), extProperty.name.identifier, valueType,
                             computeKind(extProperty), extProperty.isAnnotatedBy(StandardNames.OPEN_ANNOTATION), extProperty.isVar,
                             false, module, extPropertyId, extProperty.source)
    }

    private fun createOwnProperty(property: PropertyDescriptor,
                                  propertyId: Int,
                                  classDescriptor: ClassDescriptor,
                                  receiver: ObjClassImpl<Obj>): OwnProperty<Obj, *> {
      val valueType = convertType(property.type, "${classDescriptor.fqNameSafe.asString()}::${property.name}", property.isAnnotatedBy(StandardNames.CHILD_ANNOTATION))
      return OwnPropertyImpl(receiver, property.name.identifier, valueType, computeKind(property),
                             property.isAnnotatedBy(StandardNames.OPEN_ANNOTATION), property.isVar, false, false, propertyId,
                             property.isAnnotatedBy(StandardNames.EQUALS_BY_ANNOTATION),
                             property.source)
    }

    private fun convertType(type: KotlinType, propertyDescription: String, hasChildAnnotation: Boolean = false): ValueType<*> {
      if (type.isMarkedNullable) {
        return ValueType.Optional(convertType(type.makeNotNullable(), propertyDescription, hasChildAnnotation))
      }
      val descriptor = type.constructor.declarationDescriptor
      if (descriptor is ClassDescriptor) {
        val fqName = descriptor.fqNameSafe
        val primitive = ObjTypeConverter.findPrimitive(fqName)
        if (primitive != null) return primitive
        if (fqName == StandardNames.LIST_INTERFACE) {
          return ValueType.List(convertType(type.arguments.first().type, propertyDescription, hasChildAnnotation))
        }
        if (fqName == StandardNames.SET_INTERFACE) {
          return ValueType.Set(convertType(type.arguments.first().type, propertyDescription, hasChildAnnotation))
        }
        if (fqName == StandardNames.MAP_INTERFACE) {
          return ValueType.Map(convertType(type.arguments.first().type, propertyDescription, hasChildAnnotation),
                               convertType(type.arguments.last().type, propertyDescription, hasChildAnnotation))
        }
        if (descriptor.isEntityInterface) {
          return ValueType.ObjRef(type.isAnnotatedBy(StandardNames.CHILD_ANNOTATION) || hasChildAnnotation, //todo leave only one target for @Child annotation 
                                  findObjClass(descriptor))
        }
        val superTypes = descriptor.defaultType.supertypes().mapNotNull { it.constructor.declarationDescriptor?.fqNameSafe?.asString() }
        if (descriptor.kind == ClassKind.OBJECT) {
          return ValueType.Object<Any>(fqName.asString(), superTypes)
        }
        if (descriptor.kind == ClassKind.ENUM_CLASS) {
          return ValueType.Enum<Any>(fqName.asString())
        }
        if (descriptor.isData) {
          return ValueType.DataClass<Any>(fqName.asString(), superTypes, createProperties(descriptor))
        }
        if (descriptor.isSealed()) {
          return ValueType.SealedClass<Any>(fqName.asString(), superTypes, descriptor.sealedSubclasses.map { 
            convertType(it.defaultType, propertyDescription) as ValueType.JvmClass<*>
          })
        }
        return ValueType.Blob<Any>(fqName.asString(), superTypes)
      }                             
      throw IncorrectObjInterfaceException("Property '$propertyDescription' has unsupported type '$type'")
    }

    private fun createProperties(descriptor: ClassDescriptor): List<ValueType.DataClassProperty> {
      return descriptor.unsubstitutedMemberScope.getContributedDescriptors(DescriptorKindFilter.VARIABLES)
        .filterIsInstance<PropertyDescriptor>()
        .map { ValueType.DataClassProperty(it.name.identifier, convertType(it.type, "${descriptor.fqNameSafe.asString()}::${it.name.identifier}")) }
    }

    private fun findObjClass(descriptor: ClassDescriptor): ObjClass<*> {
      if (descriptor.containingPackage()!!.asString() == module.name) {
        return types.find { it.first.typeConstructor == descriptor.typeConstructor }?.second ?: error("Cannot find ${descriptor.fqNameSafe} in $module")
      }
      return getObjClass(descriptor)
    }

    private fun computeKind(property: PropertyDescriptor): ObjProperty.ValueKind {
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

  }
}

private fun createObjTypeStub(interfaceDescriptor: ClassDescriptor, module: CompiledObjModuleImpl): ObjClassImpl<Obj> {
  val openness = when {
    interfaceDescriptor.isAnnotatedBy(StandardNames.ABSTRACT_ANNOTATION) -> ObjClass.Openness.abstract
    interfaceDescriptor.isAnnotatedBy(StandardNames.OPEN_ANNOTATION) -> ObjClass.Openness.open
    else -> ObjClass.Openness.final
  }
  return ObjClassImpl(module, interfaceDescriptor.name.identifier, openness, interfaceDescriptor.source)
}

private fun Annotated.isAnnotatedBy(fqName: FqName) = annotations.hasAnnotation(fqName)

private object ObjTypeConverter {
  private val primitiveTypes = mapOf(
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
  
  fun findPrimitive(fqName: FqName): ValueType.Primitive<*>? = primitiveTypes[fqName.asString()]
}

private object StandardNames {
  val DEFAULT_ANNOTATION = FqName(Default::class.qualifiedName!!)
  val OPEN_ANNOTATION = FqName(Open::class.qualifiedName!!)
  val ABSTRACT_ANNOTATION = FqName(Abstract::class.qualifiedName!!)
  val CHILD_ANNOTATION = FqName(Child::class.qualifiedName!!)
  val EQUALS_BY_ANNOTATION = FqName(EqualsBy::class.qualifiedName!!)
  val LIST_INTERFACE = FqName(List::class.qualifiedName!!)
  val SET_INTERFACE = FqName(Set::class.qualifiedName!!)
  val MAP_INTERFACE = FqName(Map::class.qualifiedName!!)
}

private val entitiesSuperclassFqn = FqName(com.intellij.platform.workspace.storage.WorkspaceEntity::class.java.name)

private val ClassDescriptor.isEntityInterface: Boolean
  get() {
    // If is `WorkspaceEntity` interface we need to check it
    if (fqNameSafe == entitiesSuperclassFqn) return true
    return isInterface(this) && defaultType.isSubclassOf(entitiesSuperclassFqn)
  }

private val ClassDescriptor.isEntityBuilderInterface: Boolean
  get() {
    return isEntityInterface && name.identifier == "Builder" //todo improve
  }

private fun KotlinType.isSubclassOf(superClassName: FqName): Boolean {
  return constructor.supertypes.any { 
    it.constructor.declarationDescriptor?.fqNameSafe == superClassName || it.isSubclassOf(superClassName) 
  }
}
