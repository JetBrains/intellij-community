// IGNORE_K2
package b

import b.A.<caret>

class A {
    fun bar() {}

    companion object {
        fun foo() {}
    }
}

// INVOCATION_COUNT: 2
// EXIST: Companion
// ABSENT: bar
// ABSENT: foo

