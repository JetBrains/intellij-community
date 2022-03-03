// WITH_STDLIB
// AFTER-WARNING: Variable 'list' is never used
import java.util.HashMap

fun foo() {
    var list: HashMap<Int, Int> = <caret>HashMap()
}