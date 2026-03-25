// PROBLEM: 'minus' call iterates over the argument instead of removing it as a single element
// FIX: Convert to 'minusElement' call (changes semantics)
// IGNORE_K1
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
import java.nio.file.Path

fun test(coll: MutableCollection<Path>, path: Path) {
    coll <caret>- path
}