// PROBLEM: none
// WITH_RUNTIME
import java.util.Optional

fun test() {
    val value = Optional.of(42).orElseThrow {
        <caret>IllegalStateException()
    }
}
