package test

abstract class AClass
object Modifier {

}
abstract class ZClass

fun test(modifier: Any) {

}

fun foo() {
    test(<caret>)
}

// Modifier should be first because it perfectly matches the parameter name
// ORDER: Modifier
