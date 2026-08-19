// FIR_IDENTICAL

sealed class Foo
object Bar : Foo()
object C : Foo()

fun test(f: Foo) {
    when(f) {
        Ba<caret> -> {}
        C -> {}
    }
}

// EXIST: Bar
// ABSENT: { "lookupString": "Bar", "tailText": " -> " }
// FIR_COMPARISON
