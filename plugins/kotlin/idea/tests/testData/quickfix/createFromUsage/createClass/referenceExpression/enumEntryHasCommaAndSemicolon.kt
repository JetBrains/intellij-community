// "Create enum constant 'C'" "true"
enum class Bar {
    A,
    B,
    ;
}

fun main() {
    Bar.C<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix