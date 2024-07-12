// "Initialize with constructor parameter" "false"
// IGNORE_IRRELEVANT_ACTIONS
// ERROR: Property must be initialized or be abstract

actual class SimpleWConstructor {
    val <caret>b: String
    constructor(s: String)
    actual constructor(i: Int)
}