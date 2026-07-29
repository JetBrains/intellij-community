// "Convert to 'java.time.Instant'" "true"
// WITH_STDLIB
// RUNTIME_WITH_FULL_JDK
// K2_ERROR: INITIALIZER_TYPE_MISMATCH

import java.time.Instant as JavaInstant
import kotlin.time.Clock

val test: JavaInstant = Clock.System.no<caret>w()

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ToJavaInstantFix