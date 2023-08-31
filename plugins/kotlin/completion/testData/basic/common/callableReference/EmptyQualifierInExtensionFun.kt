class C {
    fun memberFun(){}

    val memberVal = 1

    class NestedClass
    inner class InnerClass

    companion object {
        fun companionObjectFun(){}
    }
}

fun C.foo() {
    val v = ::<caret>
}

// IGNORE_K2
// ABSENT: memberFun
// ABSENT: memberVal
// ABSENT: hashCode
// ABSENT: companionObjectFun
// ABSENT: NestedClass
// ABSENT: InnerClass
