//PROBLEM: none

private val x: Lazy<String> =<caret> lazy { "hello" }

fun test() {
    println(x.value)
}