package a

fun <T> List<T>.second(): T = this[1]
fun List<Int>.wrongReceiver(): Int = this[1]
