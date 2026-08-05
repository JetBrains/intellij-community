// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
context(s: String) val prop: Int get() = s.length

context(<caret>s: String)
fun test() {
    println(prop)
}