// "Remove redundant cast" "true"
fun foo() {}

fun main() {
    foo();
    ({ "" } as<caret> () -> String)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix