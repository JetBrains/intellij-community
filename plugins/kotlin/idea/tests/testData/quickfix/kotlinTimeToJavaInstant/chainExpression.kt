// "Convert to 'java.time.Instant'" "true"
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

import java.time.Instant as JavaInstant
import kotlin.time.Clock
import kotlin.time.Instant


val String.ktInstant: Instant
    get() = Clock.System.now()

fun takesJavaInstant(instant: JavaInstant) {}

fun test() {
    takesJavaInstant("abc".substring(0).ktInsta<caret>nt)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ToJavaInstantFix