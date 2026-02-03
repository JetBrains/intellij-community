open class A1 {
    open fun myFun(b: Boolean = false) {}
}

open class A2 : A1() {
    override fun myFun(b: Boolean) {}
}

class A3 : A2() {
    override fun myFun(b: Boolean) {}
}

fun test(a3: A3) {
    a3.myFun(false<caret>)
}