// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
context(n: Int)
fun foo(): Int = n

fun bar(){
    context(<hint text="with:"/>0) {
        foo()
    }
}