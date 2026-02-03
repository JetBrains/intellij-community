// AFTER-WARNING: Parameter 'a' is never used
fun <T> doSomething(a: T) {}

fun foo() {
    if (true) <caret>{
        //comment
        doSomething("test")
    }
}
