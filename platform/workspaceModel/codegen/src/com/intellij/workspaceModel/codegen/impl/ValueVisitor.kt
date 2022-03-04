package org.jetbrains.deft.impl

import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.get
import org.jetbrains.deft.impl.fields.ExtField
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.impl.fields.MemberOrExtField

open class ValueVisitor {
    @Suppress("UNCHECKED_CAST")
    fun visitObj(obj: Obj) {
        visitStructure(obj.factory.structure as TStructure<Obj, ObjBuilder<Obj>>, obj)
    }

    @Suppress("UNCHECKED_CAST")
    open fun <V : Obj, B : ObjBuilder<V>> visitStructure(type: TStructure<V, B>, obj: V) {
        type.allFields.forEach { field ->
            field as Field<Obj, Any?>
            visitFieldValue(obj, field, obj[field])
        }

        (obj as? ExtensibleProvider)
            ?.getExtensibleContainer()
            ?.forEachExtension { field, value ->
                field as ExtField<Obj, Any?>
                visitFieldValue(obj, field, value)
            }
    }

    open fun <T : Obj, V> visitFieldValue(obj: T, field: Field<out T, V>, value: V): Unit =
        visitFieldValue(obj, field as MemberOrExtField<out T, V>, value)

    open fun <T : Obj, V> visitFieldValue(obj: T, field: ExtField<out T, V>, value: V): Unit =
        visitFieldValue(obj, field as MemberOrExtField<out T, V>, value)

    open fun <T : Obj, V> visitFieldValue(obj: T, field: MemberOrExtField<out T, V>, value: V) {
        visitValue(field.type, value)
    }

    @Suppress("UNCHECKED_CAST")
    open fun <V> visitValue(type: ValueType<V>, value: V) =
        when (type) {
            is TString -> visitString(value as String)
            is TBoolean -> visitBoolean(value as Boolean)
            is TInt -> visitInt(value as Int)
            is TOptional<*> -> visitOptional(type as TOptional<Any>, value)
            is TRef<*> -> visitRef(type as TRef<Obj>, value as Obj)
            is TList<*> -> visitList(type as TList<Any?>, value as List<Any?>)
            is TMap<*, *> -> visitMap(type as TMap<Any?, Any?>, value as Map<Any?, Any?>)
            is TStructure<*, *> -> visitStructure(type as TStructure<Obj, ObjBuilder<Obj>>, value as Obj)
            is TBlob<*> -> visitBlob(type as TBlob<V>, value)
            else -> error(type)
        }

    open fun visitString(value: String) = Unit
    open fun visitBoolean(value: Boolean) = Unit
    open fun visitInt(value: Int) = Unit
    open fun <V> visitOptional(type: TOptional<V>, value: V?) {
        if (value != null) {
            visitValue(type.type, value)
        }
    }

    open fun <V : Obj> visitRef(type: TRef<V>, value: V) =
        if (type.child) visitChild(type.targetObjType, value)
        else visitNonChild(type.targetObjType, value)

    open fun <V : Obj, B : ObjBuilder<V>> visitChild(type: ObjType<V, B>, value: V) = Unit
    open fun <V : Obj, B : ObjBuilder<V>> visitNonChild(type: ObjType<V, B>, value: V) = Unit

    open fun <V> visitList(type: TList<V>, value: List<V>) {
        value.forEach {
            visitValue(type.elementType, it)
        }
    }

    open fun <K, V> visitMap(type: TMap<K, V>, value: Map<K, V>) {
        value.forEach { (k, v) ->
            visitValue(type.keyType, k)
            visitValue(type.valueType, v)
        }
    }

    open fun <V> visitBlob(type: TBlob<V>, value: V) = Unit
}