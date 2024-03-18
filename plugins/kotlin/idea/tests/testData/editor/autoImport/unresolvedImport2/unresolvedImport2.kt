package test

import pkg.A
import unresolved.foo

fun test() {
    val a = A()
    a.foo(1)
}