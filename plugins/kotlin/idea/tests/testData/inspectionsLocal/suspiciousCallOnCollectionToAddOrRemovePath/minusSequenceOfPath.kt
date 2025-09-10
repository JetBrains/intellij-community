// PROBLEM: 'minus' call removes Path elements
// FIX: Convert to 'minusElement' call (changes semantics)
// IGNORE_K1
// WITH_STDLIB
package java.nio.file

class Path

fun test(seq: Sequence<Path>, path: Path) {
    seq <caret>- path
}