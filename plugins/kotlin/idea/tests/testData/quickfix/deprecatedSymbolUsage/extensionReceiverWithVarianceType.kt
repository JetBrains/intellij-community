// "Replace with 'bar(f)'" "true"
// WITH_STDLIB

fun test(list: List<Int>) {
    list.foo<caret> { it }
}

@Deprecated("", ReplaceWith("bar(f)"))
fun List<out Int>.foo(f: (Int) -> Unit) {}

fun List<Int>.bar(f: (Int) -> Unit) {}
