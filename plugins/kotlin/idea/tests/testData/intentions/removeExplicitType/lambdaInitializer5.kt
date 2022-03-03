// IS_APPLICABLE: false
// WITH_STDLIB
val foo: <caret>(Pair<Int, Int>) -> String = { (i: Int, j: Int) -> (i + j).toString() }