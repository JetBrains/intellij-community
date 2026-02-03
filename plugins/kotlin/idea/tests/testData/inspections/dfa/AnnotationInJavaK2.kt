// WITH_STDLIB
internal fun bug() {
    if (<error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'storage'.">storage</error> is FileStorageAnnotation) {
        println(true)
    }
}

fun main() {
    bug(<error descr="[TOO_MANY_ARGUMENTS] Too many arguments for 'fun bug(): Unit'.">FileStorageAnnotation()</error>) // true
}