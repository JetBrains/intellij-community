// PRIORITY: LOW
package one.two.three

import one.two.three.Test.Companion.test

class Test {
    companion object {
        fun test() {

        }
    }
}

fun check() {
    ::<caret>test
}
