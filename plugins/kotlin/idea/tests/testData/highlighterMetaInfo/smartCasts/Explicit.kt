// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
interface Foo {
    fun function()
    val property: Int
}

fun foo(parameter: Any) {
    if (parameter is Foo) {
        parameter.function()
        parameter.property
    }
}
