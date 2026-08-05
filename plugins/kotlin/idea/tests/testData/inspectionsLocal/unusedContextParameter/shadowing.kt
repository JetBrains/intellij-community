// COMPILER_ARGUMENTS: -Xcontext-parameters
context(<caret>s: String)
fun test() {
    val s = "shadow"
    println(s)
}