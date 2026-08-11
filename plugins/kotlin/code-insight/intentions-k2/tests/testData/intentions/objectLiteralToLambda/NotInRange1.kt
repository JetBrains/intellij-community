// IS_APPLICABLE: false
// WITH_STDLIB

fun foo(runnable: Runnable) {}

fun bar() {
    foo(object : Runnable <caret>{
        override fun run() {
        }
    })
}