// PROBLEM: none
// IGNORE_K1
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
import java.nio.file.Path

fun test(seq1: Sequence<Path>, seq2: Sequence<Path>) {
    seq1 <caret>- seq2
}