// "Add missing actual declarations" "true"
// TEST
// K2_ACTION: "Create actual in 'a_JVMTest'" "true"
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinNoActualForExpectInspection


package test

expect fun <caret>testHelper()