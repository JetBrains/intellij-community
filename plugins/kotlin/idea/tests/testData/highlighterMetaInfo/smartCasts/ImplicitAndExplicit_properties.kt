// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
interface Bar

interface Foo {
    val Bar.regularVal: Bar

    val Bar?.nullableVal: Bar
}

fun Any.foo(parameter: Any?) {

    if (this is Foo && parameter is Bar?) {

        parameter?.regularVal

        parameter.nullableVal

    }
}