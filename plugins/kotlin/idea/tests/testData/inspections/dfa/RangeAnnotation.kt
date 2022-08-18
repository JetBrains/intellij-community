// WITH_STDLIB
package org.jetbrains.annotations

import java.io.InputStream
import kotlin.random.Random

@Target(AnnotationTarget.TYPE)
annotation class Range(
    val from: Long,
    val to: Long
)

class BooBooBoo {
    fun test(): @Range(from = 0, to = 10) Int {
        return Random(123).nextInt(0, 10)
    }

    fun main() {
        val x = test()
        if (<warning descr="Condition 'x < 0' is always false">x < 0</warning>) {
        }
    }

    fun testIS(ins: InputStream) {
        // externally annotated in Java
        if (<warning descr="Condition 'ins.read() == -2' is always false">ins.read() == -2</warning>) {}
    }
}