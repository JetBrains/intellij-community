// "class org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix" "false"
// K2_ACTION: "Replace with 'fooBar(name)'" "true"
// ACTION: Compiler warning 'DEPRECATION' options
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Convert to also
// ACTION: Convert to apply

@Deprecated(
    message = "Use the member function instead.",
    replaceWith = ReplaceWith("fooBar(name)")
)
fun JavaClass.fooBar(name: String) = TODO()

fun foo() {
    JavaClass().<selection><caret></selection>fooBar("example")
}
