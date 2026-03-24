open class Test

val other: Test = Test()
val unrelated: Int = 5

val a: Test = <caret>


// WITH_ORDER
// EXIST: other
// EXIST: Test
// EXIST: object
// EXIST: unrelated
// IGNORE_K1