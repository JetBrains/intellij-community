// SKIP_ERRORS_BEFORE
// IS_APPLICABLE: false
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
fun main() {
    val dc = DataClass(""<caret>)
}

private data class DataClass(val : String)