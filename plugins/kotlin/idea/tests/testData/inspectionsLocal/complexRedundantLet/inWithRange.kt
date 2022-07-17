// WITH_STDLIB


fun foo(list: List<Int>) {
    list.filter { it.let<caret> { it in IntRange(1, 10) } }
}