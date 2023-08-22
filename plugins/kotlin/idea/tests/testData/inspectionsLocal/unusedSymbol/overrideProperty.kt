// WITH_STDLIB
// PROBLEM: none

interface Interface {
    val foo: String
}

class Foo {
    fun m() {
        val o = object : Interface {
            override val f<caret>oo: String = "bar"
        }
    }
}