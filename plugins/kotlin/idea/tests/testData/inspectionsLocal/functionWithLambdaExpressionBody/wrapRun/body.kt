// FIX: Convert to run { ... }
// WITH_STDLIB
fun test(a: Int, b: Int) = <caret>{
    println(a)
    a + b
}