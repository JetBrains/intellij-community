// WITH_STDLIB


class MoveToTopLevelB {
    var prop = 0
    inner class InnerClass<caret>(param: Int = prop) {}
}

fun moveToTopLevelContextB() {
    MoveToTopLevelB().InnerClass()
}