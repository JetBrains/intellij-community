// "Add missing actual declarations" "true"
// ACTION: Convert function to property
// ACTION: Create test
// K2_ACTION: "Create actual in 'testProjectName.main'" "true"
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinNoActualForExpectInspection

package my.pack

expect fun isAnd<caret>roid(): Boolean
