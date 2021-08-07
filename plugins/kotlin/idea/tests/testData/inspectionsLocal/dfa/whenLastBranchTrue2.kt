// PROBLEM: none
fun test(obj2 : Any) {
    when(obj2) {
        is String -> {}
        is Int -> {}
        else -> return
    }
    when(obj2) {
        is String -> {}
        <caret>is Int -> {}
        else -> return
    }
}
class X {}
class Y {}