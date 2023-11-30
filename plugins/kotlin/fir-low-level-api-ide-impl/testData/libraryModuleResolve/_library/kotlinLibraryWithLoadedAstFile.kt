@file:OptIn(ExperimentalContracts::class)

package library

import kotlin.contracts.*

class FunctionWithContract() {
    public inline fun require(value: Boolean): Unit {
        contract {
            returns() implies value
        }
        if (!value) {
            throw IllegalArgumentException()
        }
    }
}

annotation class Anno(val name: String)

@Anno("WithFoo")
class WithAnno {}

class WithInner {
    inner class Inner {}

    fun foo(): Inner = Inner()
}

class WithFlexibleTypes {
    val str = java.util.Arrays.asList("hello").get(0)
}