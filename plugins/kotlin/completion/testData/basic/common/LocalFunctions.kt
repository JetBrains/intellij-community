fun containing() {
    open class A {
        fun xxFun1() {}
    }

    class B : A() {
        fun xxFun2() {}
    }

    fun xxFun3() {}

    fun B.test() {
        xxFun<caret>
    }
}

// WITH_ORDER
// EXIST: {"lookupString":"xxFun3","icon":"Function","attributes":""}
// EXIST: {"lookupString":"xxFun2","icon":"Method","attributes":"bold"}
// EXIST: {"lookupString":"xxFun1","icon":"Method","attributes":""}
