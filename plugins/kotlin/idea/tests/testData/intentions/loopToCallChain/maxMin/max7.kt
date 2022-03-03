// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'map{}.maxOrNull()'"
// INTENTION_TEXT_2: "Replace with 'asSequence().map{}.maxOrNull()'"
// AFTER-WARNING: Parameter 'i' is never used
import java.lang.Math.max

fun getMaxLineWidth(count: Int): Double {
    var m = 0.0
    <caret>for (i in 0..count-1) {
        m = max(m, getLineWidth(i))
    }
    return m
}

fun getLineWidth(i: Int): Double = TODO()
