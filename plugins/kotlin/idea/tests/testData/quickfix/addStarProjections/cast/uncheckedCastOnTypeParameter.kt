// "class org.jetbrains.kotlin.idea.quickfix.ChangeToStarProjectionFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.quickfix.ChangeToStarProjectionFix" "false"
fun <T> get(column: String, map: Map<String, Any>): T {
    return map[column] as <caret>T
}
