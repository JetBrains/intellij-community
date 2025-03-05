// PRIORITY: LOW
// COMPILER_ARGUMENTS: -Xwhen-guards
fun test(foo: Any, expr: Boolean, expr2: Boolean) {
    when (f<caret>oo) {
        is Foo if expr -> {
            // do something
        }
        is Bar if !expr -> {
            // do something 2
        }
        is Foo if (expr2 || expr) -> {
            // do something 3
        }
        else if expr -> {
            // do something else
        }
        else -> {
            // last stmt
        }
    }
}

class Foo
class Bar

// IGNORE_K1