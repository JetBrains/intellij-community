package one.two.three

fun Int.test() = Unit

fun <T> myWith(t: T, action: T.() -> Unit) = Unit

fun check() {
    myWith(4, <caret>Int::test)
}
