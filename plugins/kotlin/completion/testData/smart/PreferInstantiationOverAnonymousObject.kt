open class Test

val other: Test = Test()

val a: Test = <caret>


// WITH_ORDER
// EXIST: other
// EXIST: Test
// EXIST: object
// IGNORE_K1