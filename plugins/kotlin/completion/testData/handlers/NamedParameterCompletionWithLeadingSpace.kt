// FIR_COMPARISON
// FIR_IDENTICAL
fun foo(firstParam: Int, secondParam: Int) {}

fun main() {
    foo(firstParam = 2,second<caret>)
}

// AUTOCOMPLETE_SETTING: true