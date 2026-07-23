// INTENTION_TEXT: "Add 'return@`foo bar`'"

fun foo() {
    listOf(1, 2, 3).find `foo bar`@{
        <caret>true
    }
}
