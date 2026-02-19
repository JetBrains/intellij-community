// IS_APPLICABLE: false

import Outer.Inner

class Outer {
    class Inner {
        fun m() {}
    }
}

class Test(){
    fun test(){
        Inner().<caret>m()
    }
}