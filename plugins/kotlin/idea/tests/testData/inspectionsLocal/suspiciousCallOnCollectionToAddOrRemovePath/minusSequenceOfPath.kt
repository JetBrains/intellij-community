// PROBLEM: 'minus' call removes Path elements
// FIX: Convert to 'minusElement' call (changes semantics)
// IGNORE_K1
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
import java.nio.file.Path

fun test(seq: Sequence<Path>, path: Path) {
    seq <caret>- path
}