// PROBLEM: none
// WITH_RUNTIME
data class Foo(val map: Map<String, Int>) : Map<String, Int> by map

fun main() {
    Foo(mutableMapOf()).<caret>map { it to it }.toMap()
}