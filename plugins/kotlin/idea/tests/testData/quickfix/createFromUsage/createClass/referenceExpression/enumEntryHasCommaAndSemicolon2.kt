// "Create enum constant 'C'" "true"
enum class Baz {
    A,
    B, ;
}

fun main() {
    Baz.C<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix