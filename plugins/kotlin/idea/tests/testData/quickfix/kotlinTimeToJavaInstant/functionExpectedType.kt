// "Convert to 'java.time.Instant'" "true"
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
// K2_ERROR: RETURN_TYPE_MISMATCH

import java.time.Instant as JavaInstant
import kotlin.time.Clock
import kotlin.time.Instant

val ktInstant: Instant = Clock.System.now()

fun test(): JavaInstant {
    return ktInstant<caret>
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ToJavaInstantFix