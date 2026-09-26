fun main() {
    val str = "STRING"
    val any: Any = "ANY"
    listOf(any, str).forEach {
        <caret>println(it) // ← breakpoint, type `it.` + `javaClass` in Evaluation field
    }
}
// RUNTIME_TYPE: String
// AUTOCOMPLETE_SETTING: true

