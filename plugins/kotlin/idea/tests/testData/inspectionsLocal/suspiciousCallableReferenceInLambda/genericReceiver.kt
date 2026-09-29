// PROBLEM: Suspicious callable reference as the only lambda element
// FIX: Move reference into parentheses
// WITH_STDLIB

class Container<T>(val items: List<T>) {
    fun describe(): String = items.toString()
}

fun foo(containers: List<Container<String>>) {
    containers.map {<caret> it::describe }
}
