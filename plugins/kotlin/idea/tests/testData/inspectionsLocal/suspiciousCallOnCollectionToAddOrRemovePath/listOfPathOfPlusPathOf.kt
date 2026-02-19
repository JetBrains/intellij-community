// PROBLEM: 'plus' call appends Path elements
// FIX: Convert to 'plusElement' call (changes semantics)
// IGNORE_K1
// RUNTIME_WITH_FULL_JDK
import java.nio.file.Path

fun test() {
    listOf(Path.of("/")) <caret>+ Path.of("/a/b")
}