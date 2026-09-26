// WITH_STDLIB


class MoveToTopLevelB {
    var prop = 0
    fun function<caret>(param: Int = prop) {}
}

fun moveToTopLevelContextB() {
    MoveToTopLevelB().function()
}