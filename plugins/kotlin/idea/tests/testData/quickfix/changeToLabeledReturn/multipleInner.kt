// "Change to 'return@forEach'" "true"
// ACTION: Change to 'return@foo'
// ACTION: Change to 'return@forEach'
// ACTION: Do not show implicit receiver and parameter hints
// ERROR: The integer literal does not conform to the expected type Unit
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