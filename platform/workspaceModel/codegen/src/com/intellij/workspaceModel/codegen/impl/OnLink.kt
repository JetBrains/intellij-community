package org.jetbrains.deft

import org.jetbrains.deft.impl.ObjBuilderImpl
import org.jetbrains.deft.impl.ObjModules
import org.jetbrains.deft.impl.fields.Field

interface OnLink<A, B> {
    fun init(linker: ObjModules) = Unit
    fun add(a: A, b: B)
    fun remove(a: A, b: B)

    class Ref<A: Obj, B>(val field: Field<*, B>): OnLink<A, B> {
        override fun add(a: A, b: B) {
            aBuilder(a).setValue(field as Field<in Obj, Any?>, b)
        }

        override fun remove(a: A, b: B) {
            aBuilder(a).setValue(field as Field<in Obj, Any?>, null)
        }

        private fun aBuilder(a: A) = a.builder<ObjBuilderImpl<*>>()
    }

    class List<A: Obj, B>(val field: Field<*, MutableList<B>>): OnLink<A, B> {
        override fun add(a: A, b: B) {
            list(a).add(b)
        }

        override fun remove(a: A, b: B) {
            list(a).remove(b)
        }

        private fun list(a: A) = (a.builder<ObjBuilder<*>>() as Obj).getValue(field)
    }
}