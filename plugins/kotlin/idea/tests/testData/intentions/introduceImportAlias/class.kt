// AFTER-WARNING: Variable 'i' is never used
// AFTER-WARNING: Variable 'i' is never used
import Outer.Inner

class Outer {
    class Inner
}

class Test(){
    fun test(){
        val i = Inner<caret>()
    }
    fun test2(){
        val i = Inner()
    }
}