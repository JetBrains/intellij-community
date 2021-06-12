// WITH_MESSAGE: "Removed 1 import"

import java.util.LinkedHashSet

fun preprocessUsages(refUsages: Array<Any>) {
    val conflictDescriptions = LinkedHashSet<Int>()
    refUsages.toHashSet().sortedWith(Comparator { u1, u2 -> 0 })
    4.let { 42 }.also { 42 }.run {  }
    kotlin.run { 42 }.compareTo(4)
    val b: List<Short>
    val c: List<Boolean>
    val d: List<Long>
    println(42)
}