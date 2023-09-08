// FIR_COMPARISON
// FIR_IDENTICAL
fun String?.forNullableString(){}
fun Any?.forNullableAny(){}
fun String.forString(){}
fun Any.forAny(){}

fun foo(s: String?) {
    s.<caret>
}

// EXIST: { lookupString: "forNullableString", attributes: "bold", icon: "Function"}
// EXIST: { lookupString: "forNullableAny", attributes: "", icon: "Function"}
// EXIST: { lookupString: "forString", attributes: "grayed", icon: "Function"}
// EXIST: { lookupString: "forAny", attributes: "grayed", icon: "Function"}
// EXIST: { lookupString: "compareTo", attributes: "grayed", icon: "Function"}
// EXIST: { lookupString: "length", attributes: "grayed", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
// IGNORE_K2
