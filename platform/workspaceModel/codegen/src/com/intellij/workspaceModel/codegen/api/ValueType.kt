package com.intellij.workspaceModel.codegen.deft

import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import com.intellij.workspaceModel.codegen.deft.Field

sealed class ValueType<V> {
  open fun link(linker: ObjModule) = Unit
}

sealed class AtomicType<V> : ValueType<V>()
sealed class PrimitiveType<V> : AtomicType<V>()

object TBoolean : PrimitiveType<Boolean>()

object TInt : PrimitiveType<Int>()

object TString : AtomicType<String>()

class TList<E>(val elementType: ValueType<E>) : ValueType<List<E>>() {
  override fun link(linker: ObjModule) {
    elementType.link(linker)
  }
}

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
  var oppositeField: Field<*, *>? = null

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

  val allFieldsByName: Map<String, Field<out T, Any?>>
    by lazy {
      val allFieldsByName = mutableMapOf<String, Field<out T, Any?>>()
      allFields.forEach {
        allFieldsByName[it.name] = it
      }
      allFieldsByName
    }


  var minFieldId: Int = Int.MAX_VALUE
  var maxFieldId: Int = -1

  private val _declaredFields = mutableListOf<Field<T, Any?>>()
  val declaredFields: List<Field<T, Any?>>
    get() = _declaredFields

  val newFields get() = declaredFields.filter { it.base == null }

  val name: String?
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
    _allFields.addAll(_declaredFields.filter { !it.ignored })

    if (allFields.isEmpty()) {
      minFieldId = 0
      maxFieldId = 0
    }
    else {
      _allFields.forEach {
        if (it.id < minFieldId) minFieldId = it.id
        if (it.id > maxFieldId) maxFieldId = it.id
      }
      maxFieldId++
    }
  }

  fun <V> addField(field: Field<T, V>) {
    _declaredFields.add(field as Field<T, Any?>)
  }

  fun isAssignableTo(other: TStructure<*, *>): Boolean {
    var p = other
    while (true) {
      if (p == this) return true
      p = p.base ?: return false
    }
  }
}