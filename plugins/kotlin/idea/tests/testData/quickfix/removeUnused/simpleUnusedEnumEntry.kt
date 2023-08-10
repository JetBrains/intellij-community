// "Safe delete 'WORLD'" "true"
enum class MyEnum {
    HELLO,
    WORLD<caret>
}

fun main() {
    MyEnum.HELLO
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix