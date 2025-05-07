// "Add missing actual declarations" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+GenericInlineClassParameter
// K2_ACTION: "Create actual in 'testModule_JVM'" "true"
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinNoActualForExpectInspection

expect value class <caret>IC(val i: Int)
