// "Create expected class in common module testModule_Common" "true"
// DISABLE-ERRORS
// IGNORE_K2

actual class <caret>My {
    inner class Nested(val s: String) {
        fun hello() = s

        var ss = s

        class OtherNested(var d: Double) {
            val dd = d
        }
    }
}