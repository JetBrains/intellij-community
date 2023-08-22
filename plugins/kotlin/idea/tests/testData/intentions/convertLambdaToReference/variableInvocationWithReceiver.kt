fun test(f: () -> String) = f()

class Bar {
    val function: () -> String = { "" }
}

fun bar(bar: Bar) {
    test { <caret>bar.function() }
}