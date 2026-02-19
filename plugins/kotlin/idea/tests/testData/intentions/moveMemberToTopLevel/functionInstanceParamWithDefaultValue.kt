// WITH_STDLIB
// IGNORE_K1

class MoveToTopLevelB {
    var prop = 0
    fun function<caret>(param: Int = prop) {}
}

fun moveToTopLevelContextB() {
    MoveToTopLevelB().function()
}