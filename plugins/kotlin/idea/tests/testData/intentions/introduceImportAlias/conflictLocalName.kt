// AFTER-WARNING: Variable 'foo' is never used
class Outer {
    class Middle {
        class Inner {
            companion object {
                fun foo() {}
            }
        }
    }
}

class Test() {
    fun test() {
        val foo = 1
        Outer.Middle.Inner.foo<caret>()
    }
}