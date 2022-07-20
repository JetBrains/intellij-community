package com.intellij.workspaceModel.codegen.deft

import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder

sealed class ValueType<V> {
  open fun link(linker: ObjModule) = Unit
}

sealed class AtomicType<V> : ValueType<V>()
sealed class PrimitiveType<V> : AtomicType<V>()

object TBoolean : PrimitiveType<Boolean>()

object TInt : PrimitiveType<Int>()

object TString : AtomicType<String>()

abstract class TCollection<E, T: Collection<E>>(val elementType: ValueType<E>): ValueType<T>() {
  override fun link(linker: ObjModule) {
    elementType.link(linker)
  }
}

class TList<E>(elementType: ValueType<E>) : TCollection<E, List<E>>(elementType)

class TSet<E>(elementType: ValueType<E>) : TCollection<E, Set<E>>(elementType)

class TMap<K, V>(val keyType: ValueType<K>, val valueType: ValueType<V>) : ValueType<Map<K, V>>() {
  override fun link(linker: ObjModule) {
    keyType.link(linker)
    valueType.link(linker)
  }
}

class TPsiRef<T : Obj>(
  targetModuleType: Int,
  child: Boolean = false,
  relation: Boolean = false,
) : TRef<T>(targetModuleType, child, relation) {
  override fun link(linker: ObjModule) { }
}

open class TRef<T : Obj>(
  targetModuleType: Int,
  var child: Boolean = false,
  val relation: Boolean = false,
) : AtomicType<T>() {
  val targetObjTypeId: ObjType.Id = ObjType.Id(targetModuleType)
  lateinit var targetObjType: ObjType<T, *>

  override fun link(linker: ObjModule) {
    targetObjType = linker.byId[linker.typeIndex(targetObjTypeId.id)] as? ObjType<T, *>
               ?: error("Type \"${targetObjTypeId.id}\" is not registered in $linker")
  }
}

class TOptional<V>(val type: ValueType<V>) : ValueType<V?>() {
  override fun link(linker: ObjModule) {
    type.link(linker)
  }
}

open class TBlob<V>(val javaSimpleName: String) : PrimitiveType<V>()

class TStructure<T : Obj, B : ObjBuilder<T>>(
  val box: ObjType<T, B>,
  val base: TStructure<*, *>? = null
) : ValueType<T>(), Obj {
  private val _allFields: MutableList<Field<out Obj, Any?>> = mutableListOf()
  val allFields: List<Field<out T, Any?>>
    get() = _allFields as List<Field<out T, Any?>>


  private val _declaredFields = mutableListOf<Field<T, Any?>>()
  val declaredFields: List<Field<T, Any?>>
    get() = _declaredFields

  val newFields get() = declaredFields.filter { it.base == null }

  val name: String
    get() = box.name

  override fun link(linker: ObjModule) {
    _declaredFields.forEach { it.type.link(linker) }
    if (base != null) {
      val declaredFieldByName = _declaredFields.associateBy { it.name }
      base.allFields.forEach {
        val override = declaredFieldByName[it.name]
        if (override != null) override.base = it
        else _allFields.add(it)
      }
    }
    _allFields.addAll(_declaredFields.filter { !it.final })
  }

  fun <V> addField(field: Field<T, V>) {
    _declaredFields.add(field as Field<T, Any?>)
  }
}