// "Add missing actual declarations" "true"
// K2_ACTION: "Create actual in 'testModule_JVM'" "true"
// K1_TOOL: org.jetbrains.kotlin.idea.inspections.OptionalExpectationInspection
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinNoActualForExpectInspection

package testpkg

annotation class ExperimentalMultiplatform
annotation class OptionalExpectation

@ExperimentalMultiplatform
@OptionalExpectation
expect annotation class <caret>Ann