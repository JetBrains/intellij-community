// IGNORE_K1
// FIX: Add explicit parameter name to outer lambda

fun foo(f: (String) -> Unit) {}
fun bar(s: String) {}
fun baz(d: (a: Any, b: Any) -> Unit) {}

fun test() {
    foo {
        bar(it)
        foo {
            baz { a, b ->
                bar(it<caret>)
            }
        }
    }
}
