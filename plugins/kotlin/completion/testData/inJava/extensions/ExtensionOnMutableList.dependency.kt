package a

fun <T> MutableList<T>.addIfAbsent(element: T) {
    if (element !in this) add(element)
}
