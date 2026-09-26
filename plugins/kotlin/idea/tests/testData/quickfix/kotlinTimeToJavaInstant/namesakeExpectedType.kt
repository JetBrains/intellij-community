// "Convert to 'java.time.Instant'" "false"
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH

import kotlin.time.Clock
import kotlin.time.Instant as KotlinInstant

val ktInstant: KotlinInstant = Clock.System.now()

class Instant

fun takesSomething(notJavaInstant: Instant) {}

fun test() {
    takesSomething(<caret>ktInstant)
}
