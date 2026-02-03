package source

import source.X.Companion.other

class X {
    companion object {


        fun other() {
            foo()
        }
    }

    fun f() {
        bar++
    }
}


fun foo() {
    other()
    bar++
}

var bar = 1

