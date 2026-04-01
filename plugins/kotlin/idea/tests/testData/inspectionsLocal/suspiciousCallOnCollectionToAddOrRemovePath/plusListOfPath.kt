// PROBLEM: 'plus' call iterates over the argument instead of adding it as a single element
// FIX: Convert to 'plusElement' call (changes semantics)
// IGNORE_K1
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
import java.nio.file.Path

fun test(list: List<Path>, path: Path) {
    list <caret>+ path
}