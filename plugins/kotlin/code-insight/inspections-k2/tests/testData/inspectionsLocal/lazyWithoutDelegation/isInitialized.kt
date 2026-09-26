// PROBLEM: none
val x =<caret> lazy { "hello" }

fun test() {
    if (x.isInitialized()) {
        println(x.value)
    }
}