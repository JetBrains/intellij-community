// "Add missing actual declarations" "true"
// DISABLE_ERRORS
// K2_ACTION: "Create actual in 'testModule_JVM'" "true"
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinNoActualForExpectInspection

package my

import other.Another
import other.Other

expect class My<caret> : Other<Another>