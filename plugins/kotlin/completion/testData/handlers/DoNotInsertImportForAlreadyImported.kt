// KT-2424 Invoking completion adds unnecessary FQ name
// FIR_COMPARISON
// FIR_IDENTICAL

fun main(args: Array<String>) {
    throw IllegalAccessExceptio<caret> //Press Ctrl+Space and select it
}
// AUTOCOMPLETE_SETTING: true