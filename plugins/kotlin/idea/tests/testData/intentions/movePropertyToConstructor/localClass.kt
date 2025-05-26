// IS_APPLICABLE: false
// IGNORE_K1
fun function(parameter: Unit) {
    class LocalClass {
        val parameter<caret> = parameter
    }
}