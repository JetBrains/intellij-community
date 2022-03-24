// WITH_STDLIB
object X {
    var string = "foo"
}

var target = "baz"
fun main() {
    target <caret>= X.string
}
