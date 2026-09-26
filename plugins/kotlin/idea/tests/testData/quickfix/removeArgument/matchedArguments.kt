// "Remove argument" "false"
// ACTION: Add 'i =' to argument
// ACTION: Convert to also
// ACTION: Convert to apply
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Do not show hints for current method
// ACTION: Put arguments on separate lines
class Bar() {
    fun foo(s: String, i: Int) {
    }
}

fun main() {
    val b = Bar()
    b.foo("a", 1<caret>)
}