// AFTER-WARNING: Expected performance impact from inlining is insignificant. Inlining works best for functions with parameters of functional types
class Test {
    companion object Foo <caret>{}

    inline fun test() {}
}

fun Test.Foo.foo() {}
