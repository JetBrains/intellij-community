// PROBLEM: 'plus' call iterates over the argument instead of adding it as a single element
// FIX: Convert to 'plusElement' call (changes semantics)
// IGNORE_K1
// RUNTIME_WITH_FULL_JDK
import java.nio.file.Path

class PathWrapper(val path: Path) : Iterable<Path> {
    override fun iterator(): Iterator<Path> = path.iterator()
}

fun test(list: List<Path>, wrapper: PathWrapper) {
    list <caret>+ wrapper
}