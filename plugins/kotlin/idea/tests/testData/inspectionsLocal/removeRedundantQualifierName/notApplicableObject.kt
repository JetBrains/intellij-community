// PROBLEM: none
// WITH_STDLIB
class Foo {
    val prop = <caret>Obj.prop.toString()
}

object Obj {
    val prop = "Hello"
}