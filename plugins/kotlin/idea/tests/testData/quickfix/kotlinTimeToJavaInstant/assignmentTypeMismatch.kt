// "Convert to 'java.time.Instant'" "true"
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
// K2_ERROR: ASSIGNMENT_TYPE_MISMATCH

import java.time.Instant as JavaInstant
import kotlin.time.Clock

fun test () {
    var v: JavaInstant? = null
    v = Clock.System.no<caret>w()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ToJavaInstantFix