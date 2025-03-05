// PROBLEM: none
// Issue: KTIJ-29935

class Foo {
    var bar: String = ""
}

fun action() {
    Foo().bar = Foo().bar<caret>
}
