// FIR_IDENTICAL
// FIR_COMPARISON

fun String?.forNullableString(){}
fun Any?.forNullableAny(){}
fun String.forString(){}
fun Any.forAny(){}

fun foo(s: String?) {
    s?.<caret>
}

// EXIST: { lookupString: "forNullableString", attributes: "", icon: "Function"}
// EXIST: { lookupString: "forNullableAny", attributes: "", icon: "Function"}
// EXIST: { lookupString: "forString", attributes: "bold", icon: "Function"}
// EXIST: { lookupString: "forAny", attributes: "", icon: "Function"}
// EXIST: { lookupString: "compareTo", attributes: "", icon: "Method"}
