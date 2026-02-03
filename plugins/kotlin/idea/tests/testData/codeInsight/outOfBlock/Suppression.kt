// WITH_STDLIB
// OUT_OF_CODE_BLOCK: FALSE
// SKIP_ANALYZE_CHECK
// TYPE: '"'
// INSPECTION-CLASS: org.jetbrains.kotlin.idea.inspections.KotlinRedundantDiagnosticSuppressInspection
// NO-INSPECTION-OUTPUT
// inspection message `[GENERIC_ERROR_OR_WARNING:8] Redundant suppression` should not appear, therefore `NO-INSPECTION-OUTPUT`
fun convertToMap(converted: Map<*, *>): Map<String, Any>? {
    @Suppress("UNCHECKED_CAST")
    return converted as Map<String, Any>?
}

fun main() {
    val s = "hello<caret>
    when (s) {
        "hello" -> println(s)
        else -> {}
    }
}