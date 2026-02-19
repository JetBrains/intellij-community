// IS_APPLICABLE: false
class Bar {
    var x: Foo = Foo()
}

class Foo {
    var x: Int = 1
}

fun main() {
    val bar = Bar()
    // not allowed because it'd change semantic because
    // of side effects of getters and setters
    bar.x.x <caret>+= 1
}
