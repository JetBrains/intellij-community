// FIX: Move reference into parentheses
// WITH_STDLIB

fun foo() {
    listOf(1,2,3).map {<caret> bar -> bar::toString }
}