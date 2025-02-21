// "Add missing actual declarations" "false"
// ACTION: Convert function to property
// ERROR: Expected function 'foo' has no actual declaration in module testModule_JVM for JVM
// K2_ACTION: "Create actual in 'testModule_JVM'" "true"
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinNoActualForExpectInspection

expect fun <caret>foo(): Int
