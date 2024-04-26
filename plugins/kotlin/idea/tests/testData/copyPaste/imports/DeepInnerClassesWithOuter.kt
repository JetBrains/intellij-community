package a

import a.Outer.Nested.*
import a.Outer.Inner.*
import a.Outer.*

class Outer {
    class Nested {
        class NN {
        }
        class NN2 {
        }
        inner class NI {
        }
        inner class NI2 {
        }
    }

    inner class Inner {
        inner class II {
        }
        inner class II2 {
        }
    }
}

fun <T> with(v: T, body: T.() -> Unit) = v.body()

<selection>fun f(p1: Outer.Nested.NN, p2: Outer.Nested.NI, p3: Outer.Inner.II) {
    Outer.Nested.NN2()
    with(Outer.Nested()) {
        NI2()
    }
    with(Outer().Inner()) {
        II2()
    }
}</selection>