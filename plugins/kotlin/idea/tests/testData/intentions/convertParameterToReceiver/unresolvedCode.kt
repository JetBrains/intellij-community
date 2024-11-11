// DISABLE-ERRORS

fun main() {
    foo(listOf(1, 2, 3))
}

fun foo(lst<caret> : UnresolvedClass) {
    println(lst)
}