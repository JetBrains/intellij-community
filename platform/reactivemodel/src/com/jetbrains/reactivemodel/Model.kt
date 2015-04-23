package com.jetbrains.reactivemodel

import com.jetbrains.reactivemodel.ValueDiff

public trait Model {
    fun diff(other: Model) : Diff<Model>? {
        if (other == this) {
            return null
        }
        if (other.javaClass != javaClass) {
            return ValueDiff(other)
        }
        return diffImpl(other)
    }

    fun diffImpl(other: Model) : Diff<Model>?

    fun patch(diff: Diff<Model>) : Model
}

public trait Diff<out M: Model>

public data class ValueDiff<T: Model>(val newValue: T): Diff<Model>


trait AssocModel<in K, out TThis: AssocModel<K, *>>: Model {
    fun assoc(key: K, value: Model?): TThis
    fun find(key: K): Model?
}
