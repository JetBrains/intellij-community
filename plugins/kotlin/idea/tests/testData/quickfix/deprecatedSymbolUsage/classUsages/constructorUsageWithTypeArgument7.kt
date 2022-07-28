// "Replace with 'New'" "true"
// WITH_RUNTIME
// ERROR: Not enough information to infer type variable T

abstract class Main<T>

@Deprecated("", ReplaceWith("New"))
class Old<T, F> : Main<T>()

class New<T> : Main<T>()

fun test() {
    val main = <caret>Old<Int, String>()
}