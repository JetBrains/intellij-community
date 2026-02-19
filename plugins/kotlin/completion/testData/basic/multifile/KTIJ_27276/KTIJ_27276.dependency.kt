package foo

import foo.Foo.foo
import bar.foo

val myTestVariable = foo() // [OVERLOAD_RESOLUTION_AMBIGUITY] error

object Foo {
    fun foo() {}
}
