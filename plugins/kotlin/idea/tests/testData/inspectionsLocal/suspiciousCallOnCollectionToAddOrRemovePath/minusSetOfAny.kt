// PROBLEM: 'minus' call iterates over the argument instead of removing it as a single element
// FIX: Convert to 'minusElement' call (changes semantics)

// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
import java.nio.file.Path

fun test(set: Set<Any>, path: Path) {
    set <caret>- path
}