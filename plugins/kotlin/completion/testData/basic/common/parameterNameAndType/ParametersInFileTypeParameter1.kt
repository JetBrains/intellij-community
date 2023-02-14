// FIR_COMPARISON
// FIR_IDENTICAL
package ppp

import java.io.*

class X<T1> {
    fun <T2> f(xxxValue1: T1, xxxValue2: T2, xxxValue3: (T2) -> Unit, xxxValue4: List<T1>, xxxValue5: (T1) -> Unit){}

    fun foo(xxx<caret>)
}

// EXIST: { itemText: "xxxValue1: T1", tailText: null }
// EXIST: { itemText: "xxxValue4: List<T1>", tailText: " (kotlin.collections)" }
// EXIST: { itemText: "xxxValue5: (T1) -> Unit", tailText: null }
// NOTHING_ELSE
