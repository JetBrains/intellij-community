// FIR_COMPARISON
// FIR_IDENTICAL
package ppp

import java.io.*

class X {
    public class PublicNested
    private class PrivateNested

    fun f1(nested: PublicNested) { }
    fun f2(nested: PrivateNested) { }
    fun f3(nestedList: List<PublicNested>) { }
    fun f4(nestedList: List<PrivateNested>) { }
}

fun foo(neste<caret>)

// EXIST: { itemText: "nested: X.PublicNested", tailText: " (ppp)" }
// EXIST: { itemText: "nestedList: List<X.PublicNested>", tailText: " (kotlin.collections)" }
// NOTHING_ELSE
