// AFTER-WARNING: Variable 'i' is never used
import Outer.Middle as P

class Outer {
    class Middle {
        class Inner
    }
}

class Test() {
    fun test() {
        val i = Outer.Middle<caret>.Inner()
    }
}