// FIR_IDENTICAL
// FIR_COMPARISON

fun String.forString(){}
fun Any.forAny(){}

fun <T> T.forT() {}

fun f(pair: Pair<out Any, out Any>) {
    if (pair.first !is String) return
    pair.first.<caret>
}

// EXIST: { lookupString: "hashCode", attributes: "bold" }
// EXIST: { lookupString: "forAny", attributes: "bold" }

// EXIST: { lookupString: "forT", attributes: "" }
