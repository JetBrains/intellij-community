// "Add missing actual declarations" "true"
// K2_ACTION: "Create actual in 'testModule_JVM'" "true"
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.KotlinNoActualForExpectInspection

expect interface <caret>I {
    fun f(p: Int = 1)
}