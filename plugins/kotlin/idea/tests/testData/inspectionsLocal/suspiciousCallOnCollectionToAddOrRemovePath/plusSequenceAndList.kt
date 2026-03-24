// PROBLEM: none
// IGNORE_K1
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
import java.nio.file.Path

fun test(seq: Sequence<Path>, list: List<Path>) {
    seq <caret>+ list
}