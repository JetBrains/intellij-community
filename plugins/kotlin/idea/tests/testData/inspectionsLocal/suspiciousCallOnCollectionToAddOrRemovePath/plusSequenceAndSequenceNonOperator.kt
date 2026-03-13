// PROBLEM: none
// IGNORE_K1
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
import java.nio.file.Path

fun test(seq1: Sequence<String>, seq2: Sequence<String>) {
    seq1.plus<caret>(seq2)
}