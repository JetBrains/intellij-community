// "Add missing actual declarations" "true"
// K2_ACTION: "Create actual in 'proj_JVM'" "true"
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.KotlinNoActualForExpectInspection

import kotlin.random.Random

expect class My<caret>Generator {
    fun generate(): Random
}