// "Add missing actual declarations" "true"
// TOOL: org.jetbrains.kotlin.idea.inspections.OptionalExpectationInspection

package kotlin

annotation class ExperimentalMultiplatform
annotation class OptionalExpectation

@ExperimentalMultiplatform
@OptionalExpectation
expect annotation class <caret>Ann