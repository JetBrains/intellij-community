// RUNTIME_WITH_FULL_JDK
// AFTER-WARNING: Parameter 'f' is never used

import java.util.*

fun foo(f: () -> ArrayDeque<*>) {}

fun test() {
    foo <caret>{ ArrayDeque<Int>() }
}
