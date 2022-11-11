// "Change to 'return@foo'" "true"
// ACTION: Change to 'return@foo'
// ACTION: Change to 'return@forEach'
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