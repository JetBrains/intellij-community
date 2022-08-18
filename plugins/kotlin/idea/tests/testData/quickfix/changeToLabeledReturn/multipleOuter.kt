// "Change to 'return@foo'" "true"
// ACTION: Change to 'return@foo'
// ACTION: Change to 'return@forEach'
// ACTION: Do not show implicit receiver and parameter hints
// WITH_STDLIB

fun foo(f:()->Int){}

fun bar() {

    foo {
        listOf(1).forEach {
            return<caret> 1
        }
        return@foo 1
    }
}