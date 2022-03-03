// WITH_STDLIB

fun Int.foo() {
    let<caret> { it }.also(::println)
}