// FIR_IDENTICAL
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
