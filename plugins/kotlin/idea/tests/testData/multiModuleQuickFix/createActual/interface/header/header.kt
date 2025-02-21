// "Add missing actual declarations" "true"
// K2_ACTION: "Create actual in 'testModule_JVM'" "true"
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinNoActualForExpectInspection

expect interface <caret>Interface {
    fun foo(param: String): Int

    fun String.bar(y: Double): Boolean

    val isGood: Boolean

    var status: Int

    class Nested {
        fun bar()
    }
}