// COMPILER_ARGUMENTS: -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
package a

import a.E.A
import a.E.entries
import a.E.valueOf
import a.E.values

enum class E {
    A {
        override val foo: Int = 65
    },
    B {
        override val foo: Int? = null
    };

    abstract val foo: Int?
}

<selection>fun E.test() {
    A
    foo
    entries
    valueOf("A")
    values()
}</selection>