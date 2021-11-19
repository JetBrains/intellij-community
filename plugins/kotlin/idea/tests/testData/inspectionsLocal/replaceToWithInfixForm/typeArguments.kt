// PROBLEM: none
// WITH_STDLIB
class Receiver(val x: Int = 0)
class Argument(val y: Int = 1)

val test = <caret>"".to<String, Receiver.(Argument) -> Unit> {
    println(x)
    println(it.y)
}