package a

// Should be sorted, ignoring backticks
import b.g
import b.`fun`
import b.`val`

class Foo(
    fun bar() {
        g()
        `fun`()
        `val`()
    }
)