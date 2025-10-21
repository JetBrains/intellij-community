// WITH_STDLIB
// NO_TEMPLATE_TESTING
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Parameter 'v' is never used
var a: String?
    get() = ""
    set(v) {}

fun main(args: Array<String>) {
    doSomething(a<caret>!!)
}

fun doSomething(a: Any){}
