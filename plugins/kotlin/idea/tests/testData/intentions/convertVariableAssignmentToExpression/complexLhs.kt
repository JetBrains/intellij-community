// WITH_STDLIB
object X {
    var string = "foo"
}

fun main() {
    X.string <caret>= "bar"
}
