// WITH_STDLIB
// PROBLEM: none
// IGNORE_K1
// IGNORE_K2
class Optional<out T>(val value: T)

val Any?.foo get() = println("foo: $this")

fun main() {
    val b: Optional<Any?>? = Optional(null)
    b?.let<caret> { it.value.foo }
}
