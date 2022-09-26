// AFTER-WARNING: Expected performance impact from inlining is insignificant. Inlining works best for functions with parameters of functional types
class Test {
    companion object <caret>{};

    inline fun test() {}
}

fun Test.Companion.foo() {}
