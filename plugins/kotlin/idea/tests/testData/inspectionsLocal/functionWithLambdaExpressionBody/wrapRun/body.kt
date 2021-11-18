// FIX: Convert to run { ... }
// WITH_RUNTIME
fun test(a: Int, b: Int) = <caret>{
    println(a)
    a + b
}