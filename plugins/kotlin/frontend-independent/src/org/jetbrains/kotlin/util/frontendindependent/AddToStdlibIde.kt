package org.jetbrains.kotlin.util.frontendindependent

// TODO: replace with firstNotNullOfOrNull
inline fun <T, R : Any> Iterable<T>.firstNotNullResult(transform: (T) -> R?): R? {
    for (element in this) {
        val result = transform(element)
        if (result != null) return result
    }
    return null
}
