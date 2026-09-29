// PROBLEM: Suspicious callable reference as the only lambda element
// FIX: Move reference into parentheses
// WITH_STDLIB

fun <R> f(a: Int = 1, action: () -> R) {}

fun g(): String = ""

fun caller() {
    f {<caret> ::g }
}
