// PROBLEM: none
// WITH_STDLIB
import kotlin.collections.map as mapNotNull

val x = listOf("1").<caret>mapNotNull { it.toInt() }