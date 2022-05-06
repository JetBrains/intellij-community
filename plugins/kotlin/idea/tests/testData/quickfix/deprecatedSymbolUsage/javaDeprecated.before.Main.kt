// "class org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix" "false"
// ACTION: Compiler warning 'DEPRECATION' options
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Convert to also
// ACTION: Convert to apply
// ACTION: Do not show return expression hints
fun foo() {
    val c = JavaClass()
    c.<caret>oldFun()
}
