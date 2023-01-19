// FIR_IDENTICAL
// FIR_COMPARISON

fun String.forString(){}
fun Any.forAny(){}

fun <T> T.forT() {}

fun f(pair: Pair<out Any, out Any>) {
    if (pair.first !is String) return
    pair.first.<caret>
}

// EXIST: { lookupString: "length", attributes: "grayed", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
// EXIST: { lookupString: "hashCode", attributes: "bold", icon: "Method"}
// EXIST: { lookupString: "forString", attributes: "grayed", icon: "Function"}
// EXIST: { lookupString: "forAny", attributes: "bold", icon: "Function"}

/*TODO: { lookupString: "forT", attributes: "" }*/
// EXIST: { lookupString: "forT", attributes: "grayed", icon: "Function"}
