// COMPILER_ARGUMENTS: -Xcontext-parameters
annotation class Marker
fun foo() {}

@Marker
context(<caret>s: String)
fun test() {
    foo()
}