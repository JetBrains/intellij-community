// WITH_STDLIB
// IGNORE_K1

class MoveToTopLevelB {
    var prop = 0
    inner class InnerClass<caret>(param: Int) {
        fun test() { println(this@MoveToTopLevelB) }
    }
}

fun moveToTopLevelContextB() {
    MoveToTopLevelB().InnerClass(5)
}