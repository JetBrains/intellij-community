// WITHOUT_CUSTOM_LINE_INDENT_PROVIDER

fun test(b: Boolean) {
    if (b) {
    }
    <caret>
    else if (!b) {
    }
}