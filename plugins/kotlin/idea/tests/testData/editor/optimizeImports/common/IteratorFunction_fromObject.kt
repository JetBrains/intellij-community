// IGNORE_K2
package test

import test1.MyClass
import test1.MyObject.iterator

fun foo() {
    val s = MyClass()
    for (i in s) {}
}
