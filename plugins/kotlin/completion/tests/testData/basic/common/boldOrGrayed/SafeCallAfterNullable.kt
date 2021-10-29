fun String?.forNullableString(){}
fun Any?.forNullableAny(){}
fun String.forString(){}
fun Any.forAny(){}

fun foo(s: String?) {
    s?.<caret>
}

// EXIST: { lookupString: "forNullableString", attributes: "", icon: "nodes/function.svg"}
// EXIST: { lookupString: "forNullableAny", attributes: "", icon: "nodes/function.svg"}
// EXIST: { lookupString: "forString", attributes: "bold", icon: "nodes/function.svg"}
// EXIST: { lookupString: "forAny", attributes: "", icon: "nodes/function.svg"}
// EXIST: { lookupString: "compareTo", attributes: "", icon: "nodes/method.svg"}
