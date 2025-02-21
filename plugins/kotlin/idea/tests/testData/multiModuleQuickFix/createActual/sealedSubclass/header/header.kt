// "Add missing actual declarations" "true"
// K2_ACTION: "Create actual in 'testModule_JS'" "true"
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinNoActualForExpectInspection

expect sealed class Sealed

expect object Obj : Sealed

expect class <caret>Klass(x: Int) : Sealed
