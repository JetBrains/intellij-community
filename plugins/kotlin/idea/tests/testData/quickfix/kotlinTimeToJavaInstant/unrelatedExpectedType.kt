// "Convert to 'java.time.Instant'" "false"
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK

import kotlin.time.Clock
import kotlin.time.Instant

val ktInstant: Instant = Clock.System.now()

fun takesSomething(notInstant: Any) {}

fun test() {
    takesSomething(<caret>ktInstant)
}
