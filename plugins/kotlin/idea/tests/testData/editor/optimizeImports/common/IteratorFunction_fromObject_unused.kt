package test

import test1.MyClass
import test1.MyIteratorProvider
import test1.MyObject.iterator

class Container : MyIteratorProvider {

    fun foo() {
        val s = MyClass()
        for (i in s) {}
    }

}
