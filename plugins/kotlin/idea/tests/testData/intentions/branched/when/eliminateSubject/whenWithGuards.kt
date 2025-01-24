// PRIORITY: LOW
// COMPILER_ARGUMENTS: -Xwhen-guards
fun test(foo: Any, expr: Boolean) {
    when (f<caret>oo) {
        is Foo if expr -> {
            // do something
        }
    }
}

class Foo

// IGNORE_K1