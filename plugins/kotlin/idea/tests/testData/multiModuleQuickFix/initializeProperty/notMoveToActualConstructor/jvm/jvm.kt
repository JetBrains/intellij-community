// "Move to constructor parameters" "false"
// IGNORE_IRRELEVANT_ACTIONS
// ERROR: Property must be initialized or be abstract

actual class SimpleWConstructor actual constructor(i: Int) {
    actual val <caret>b: String
}