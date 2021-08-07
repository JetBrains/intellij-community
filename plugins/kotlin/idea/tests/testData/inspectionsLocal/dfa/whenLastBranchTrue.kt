// PROBLEM: none
fun test(obj : Boolean, obj2 : Any) {
    when(obj) {
        true -> {}
        <caret>false -> {}
    }
    when(obj2) {
        is String -> {}
        is Int -> {}
        else -> return
    }
    when(obj2) {
        is String -> {}
        is Int -> {}
        else -> return
    }
}
class X {}
class Y {}