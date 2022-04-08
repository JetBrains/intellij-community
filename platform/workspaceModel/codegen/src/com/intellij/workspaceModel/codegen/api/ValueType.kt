package org.jetbrains.deft.impl

import org.jetbrains.deft.*
import org.jetbrains.deft.impl.fields.Field

sealed class ValueType<V> {
    @ObjModule.InitApi
    open fun link(linker: ObjModules) = Unit
}

sealed class AtomicType<V> : ValueType<V>()
sealed class PrimitiveType<V> : AtomicType<V>() {}

object TBoolean : PrimitiveType<Boolean>()

object TInt : PrimitiveType<Int>()

object TString : AtomicType<String>()

class TList<E>(val elementType: ValueType<E>) : ValueType<List<E>>() {
    @ObjModule.InitApi
    override fun link(linker: ObjModules) {
        elementType.link(linker)
    }
}

class TMap<K, V>(val keyType: ValueType<K>, val valueType: ValueType<V>) : ValueType<Map<K, V>>() {
    @ObjModule.InitApi
    override fun link(linker: ObjModules) {
        keyType.link(linker)
        valueType.link(linker)
    }
}

class TRef<T : Obj>(
    targetModule: String,
    targetModuleType: Int,
    var child: Boolean = false,
    val relation: Boolean = false,
) : AtomicType<T>() {
    val targetObjTypeId: ObjType.Id<T, *> = ObjType.Id(ObjModule.Id(targetModule), targetModuleType)
    lateinit var targetObjType: ObjType<T, *>
    var oppositeField: Field<*, *>? = null

    @ObjModule.InitApi
    override fun link(linker: ObjModules) {
        targetObjType = linker[targetObjTypeId]
    }
}

class TOptional<V>(val type: ValueType<V>) : ValueType<V?>() {
    @ObjModule.InitApi
    override fun link(linker: ObjModules) {
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

    override val name: String?
        get() = box.name

    @ObjModule.InitApi
    override fun link(linker: ObjModules) {
        _declaredFields.forEach { it.type.link(linker) }
        if (base != null) {
            val declaredFieldByName = _declaredFields.associateBy { it.name }
            base.allFields.forEach {
                val override = declaredFieldByName[it.name]
                if (override != null) override.base = it
                else _allFields.add(it)
            }
        }
        _allFields.addAll(_declaredFields)

        if (allFields.isEmpty()) {
            minFieldId = 0
            maxFieldId = 0
        } else {
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