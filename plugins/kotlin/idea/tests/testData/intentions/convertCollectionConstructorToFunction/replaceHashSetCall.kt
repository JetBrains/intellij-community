// WITH_RUNTIME
// AFTER-WARNING: Variable 'list' is never used
import java.util.HashSet

fun foo() {
    var list: HashSet<Int> = <caret>HashSet()
}