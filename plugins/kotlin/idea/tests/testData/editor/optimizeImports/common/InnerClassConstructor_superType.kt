package test

import test.Outer.InnerBase

open class Outer {
    inner open class InnerBase

    inner class InnerChild : InnerBase()
}