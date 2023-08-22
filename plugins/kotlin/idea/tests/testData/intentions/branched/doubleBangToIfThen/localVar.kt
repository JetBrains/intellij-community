// WITH_STDLIB
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'args' is never used
fun main(args: Array<String>) {
    var a: String? = "A"
    doSomething(a<caret>!!)
}

fun doSomething(a: Any){}
