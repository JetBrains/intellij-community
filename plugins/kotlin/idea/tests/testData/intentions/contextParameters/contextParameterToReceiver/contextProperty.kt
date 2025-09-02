// COMPILER_ARGUMENTS: -Xcontext-parameters

context(<caret>c1: String)
var prop: Int
    get() = c1.length
    set(value) {
        println(c1)
        foo()
        c1.bar()
    }

context(c: String)
fun foo() {
}

fun String.bar() {
}
