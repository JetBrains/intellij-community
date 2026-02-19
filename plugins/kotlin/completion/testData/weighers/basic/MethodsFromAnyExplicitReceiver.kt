// FIR_COMPARISON
// FIR_IDENTICAL

interface Common {
    fun xxFun1()
}

class A<T> : Common {
    override fun xxFun1() {}

    fun xxFun2() {}
}

class B : Common {
    override fun xxFun1() {}

    val a = A<Int>()

    fun test() {
        a.<caret>
    }
}

// ORDER: xxFun2
// ORDER: xxFun1
// ORDER: equals
// ORDER: hashCode
// ORDER: toString
