// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
interface Base

interface Foo {
    operator fun Base.invoke() {}
}

fun Foo.test(param: Any) {
    if (param is Base) {

        param.invoke() // call 1

        (param)() // call 2

        param() // call 3
    }
}