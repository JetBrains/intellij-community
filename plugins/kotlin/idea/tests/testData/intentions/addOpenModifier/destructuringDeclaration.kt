// IS_APPLICABLE: false
// K2_ERROR: NON_LOCAL_DESTRUCTURING_DECLARATION

data class Bar(val foo: Int, val bar: Int)

open class Foo {
    <caret>var (foo, bar) = Bar(1, 1)
}