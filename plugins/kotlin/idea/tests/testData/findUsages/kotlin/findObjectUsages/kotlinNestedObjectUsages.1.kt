package b

import a.A.O
import a.A

class Client {
    fun fooBar() {
        val a = A()

        println("foo = ${A.O.foo}")
        val obj = A.O
        println("length: ${obj.foo.length}")
    }
}