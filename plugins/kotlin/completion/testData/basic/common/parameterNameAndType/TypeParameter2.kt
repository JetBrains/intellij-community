// FIR_COMPARISON
package pack

class Xxxv

fun <Xxxv> foo(xxx<caret>) {}

// EXIST: { lookupString: "xxxv: Xxxv", itemText: "xxxv: Xxxv", typeText: "<Xxxv> defined in pack.foo", icon: "Class"}
// EXIST: { lookupString: "xxxv: Xxxv", itemText: "xxxv: Xxxv", tailText:" (pack)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// NOTHING_ELSE