package test

import test.Foo.callableReference
import test.Foo.extFuncFromBase
import test.Foo.extPropFromBase
import test.Foo.funcFromBase
import test.Foo.invoke
import test.Foo.propFromBase

open class Base {
    fun funcFromBase() {}
    fun Int.extFuncFromBase() {}

    val propFromBase: Int = 0
    val Int.extPropFromBase: Int get() = 0

    operator fun String.invoke() {}

    fun callableReference() {}
}

object Foo : Base()

fun usage() {
    funcFromBase()
    10.extFuncFromBase()

    propFromBase
    10.extPropFromBase

    "hello"()

    ::callableReference
}