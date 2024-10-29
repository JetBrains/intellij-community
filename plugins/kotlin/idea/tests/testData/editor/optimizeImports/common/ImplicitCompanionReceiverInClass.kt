package test

import test.Base.Companion.FromCompanionFirst
import test.Base.Companion.FromCompanionSecond
import test.Base.Companion.companionFunFirst
import test.Base.Companion.companionFunSecond

open class Base {
    companion object {
        class FromCompanionFirst
        class FromCompanionSecond

        fun companionFunFirst() {}
        fun companionFunSecond() {}
    }

    fun usageBase() {
        FromCompanionFirst()

        companionFunFirst()
    }
}

class Child : Base() {
    fun usageChild() {
        FromCompanionSecond()

        companionFunSecond()
    }
}