// WITH_STDLIB


class MoveToTopLevelB {
    var prop = 0
    inner class InnerClass<caret>(param: Int) {
        fun test() { println(this@MoveToTopLevelB) }
    }
}

fun moveToTopLevelContextB() {
    MoveToTopLevelB().InnerClass(5)
}