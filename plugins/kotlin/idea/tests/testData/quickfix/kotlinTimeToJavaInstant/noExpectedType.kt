// "Convert to 'java.time.Instant'" "false"
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK

import java.time.Instant as JavaInstant
import kotlin.time.Clock
import kotlin.time.Instant

val ktInstant: Instant = Clock.System.now()

fun test() {
    <caret>ktInstant
}
