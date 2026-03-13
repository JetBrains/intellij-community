// PROBLEM: 'minus' call removes Path elements
// FIX: Convert Path to explicit Collection
// PRIORITY: LOW
// IGNORE_K1
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
import java.nio.file.Path

class Path {
    fun toSet(): Set<Path> = TODO()
}

fun test(list: List<Path>, path: Path) {
    list <caret>- path
}