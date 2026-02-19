// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
interface Bar

interface Foo {
    operator fun Bar.plus(other: Bar)
    operator fun Bar.unaryPlus()

    infix fun Bar.customOperator(other: Bar) {}
}

fun Any.foo(parameter: Any) {

    if (this is Foo && parameter is Bar) {

        parameter + parameter

        +parameter

        parameter customOperator parameter

    }
}