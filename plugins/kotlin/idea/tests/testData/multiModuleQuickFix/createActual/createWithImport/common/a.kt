// "Add missing actual declarations" "true"
// K2_ACTION: "Create actual in 'testModule_JVM'" "true"
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinNoActualForExpectInspection

package a

import b.B

expect class <caret>A : B
