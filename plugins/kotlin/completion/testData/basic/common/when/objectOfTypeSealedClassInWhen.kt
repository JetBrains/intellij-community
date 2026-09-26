// FIR_IDENTICAL

sealed class Foo
object Bar : Foo()

fun test(f: Foo) {
    when(f) {
        Ba<caret> -> {}
    }
}

// EXIST: Bar
// ABSENT: { "lookupString": "Bar", "tailText": " -> " }
// FIR_COMPARISON
