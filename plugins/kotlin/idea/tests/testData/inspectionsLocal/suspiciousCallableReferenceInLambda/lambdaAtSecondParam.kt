// PROBLEM: Suspicious callable reference as the only lambda element
// FIX: Move reference into parentheses
// WITH_STDLIB

import kotlin.reflect.KFunction

fun g(): String = ""

fun <R> f(first: () -> KFunction<*>, second: () -> R) {}

fun caller() {
    val k: KFunction<*> = ::g
    f({ k }, {<caret> ::g })
}
