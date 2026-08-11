// "Convert to 'java.time.Instant'" "false"
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH

import java.time.Instant as JavaInstant
import kotlin.time.Clock
import kotlin.time.Instant

val ktInstant: Instant? = Clock.System.now()

fun takesJavaInstant(instant: JavaInstant) {}

fun test() {
    takesJavaInstant(<caret>ktInstant)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ToJavaInstantFix