package my.simple.name

import my.simple.name.Outer.Middle.Inner.Companion.check

class Outer {
    class Middle {
        class Inner {
            companion object {
                fun check() {}
            }
        }
    }
}

fun main() {
    my.simple.name.Outer.Middle.Inner.check()
    Outer.Middle.Inner<caret>.check()
}

// IGNORE_FIR

// K2 implementation removes qualifiers from both of `my.simple.name.Outer.Middle.Inner.check()` and `Outer.Middle.Inner<caret>.check()`.
// The result of this test makes them `check()` and `check()`. It is actually a correct result.