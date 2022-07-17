// PROBLEM: none
// WITH_STDLIB
import java.util.Optional

fun test() {
    val value = Optional.of(42).orElseThrow {
        <caret>IllegalStateException()
    }
}
