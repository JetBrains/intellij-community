// "Create enum constant 'C'" "true"
enum class E {
    A, B
}

fun foo() {
    E.<caret>C
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix