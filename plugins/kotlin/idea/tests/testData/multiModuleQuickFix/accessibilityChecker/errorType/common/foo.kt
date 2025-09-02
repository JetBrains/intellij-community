// "Add missing actual declarations" "true"
// K2_ACTION: "Create actual in 'testModule_JVM'" "true"
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinNoActualForExpectInspection
// SHOULD_FAIL_WITH: Cannot generate class: Type &lt;Unknown&gt; is not accessible from target module
// DISABLE_ERRORS
// IGNORE_K2

expect class Foo<caret> {
    var bar
}