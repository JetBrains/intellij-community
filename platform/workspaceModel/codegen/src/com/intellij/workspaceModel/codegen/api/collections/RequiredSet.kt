package org.jetbrains.deft.collections

/**
 * Set with declared [required] elements that shouldn't be removed
 */
interface RequiredSet<T> : Set<T> {
    fun isRequired(element: T): Boolean
    fun isOptional(element: T): Boolean

    class RequiredElementException(val set: RequiredSet<*>, val element: Any?) : Exception() {
        override fun toString(): String = "Cannot remove required `$element` from `$set`"
    }
}

interface MutableRequiredSet<T> : RequiredSet<T>, MutableSet<T> {
    fun addOptional(element: T): Boolean
    fun addAllOptional(elements: Collection<T>): Boolean
}