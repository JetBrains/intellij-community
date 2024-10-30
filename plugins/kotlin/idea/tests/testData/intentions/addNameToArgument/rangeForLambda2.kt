// PRIORITY: LOW
// AFTER-WARNING: Parameter 'handler' is never used
fun foo(handler: () -> Unit){}

fun bar() {
    foo({ }<caret>)
}