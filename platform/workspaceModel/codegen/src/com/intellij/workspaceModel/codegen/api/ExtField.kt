package org.jetbrains.deft.impl.fields

import org.jetbrains.deft.*
import org.jetbrains.deft.impl.ObjModule
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.ValueType
import kotlin.reflect.KProperty

class ExtField<P : Obj, V>(
    val id: ExtFieldId,
    owner: ObjType<P, *>,
    name: String,
    type: ValueType<V>
) : MemberOrExtField<P, V>(owner, name, type) {
    /* internal */lateinit var default: (parent: P) -> V

    fun newValue(parent: P): V = default(parent)

    // Companion (ObjType def)
    operator fun getValue(obj: ObjType<P, *>, property: KProperty<*>): ExtField<P, V> =
        this

    // Value interface
    operator fun getValue(obj: P, property: KProperty<*>): V? =
        obj.getExtension(this)

    // Builder interface
//    todo: this method required only in case Builder is not Obj (currently it is)
//    operator fun getValue(builder: ObjBuilder<P>, property: KProperty<*>): V =
//        builder.getOrCreateExtension(this)

    operator fun setValue(builder: ObjBuilder<P>, property: KProperty<*>, value: V?) {
        if (value != null) {
            builder.addExtension(this, value)
        } else {
            builder.removeExtension(this)
        }
    }

}