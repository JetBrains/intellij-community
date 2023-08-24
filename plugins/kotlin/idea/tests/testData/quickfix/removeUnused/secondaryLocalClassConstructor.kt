// "Safe delete constructor" "true"
fun main() {
    class LocalClass(val number: Int) {
        <caret>constructor(s: String) : this(s.toInt())
    }

    val l = LocalClass(42)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix