private val x =<caret> lazy { "hello" }

fun test() {
    println(x.value)
}