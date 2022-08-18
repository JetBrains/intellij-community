// FIR_IDENTICAL
// FIR_COMPARISON
interface T1 {
    fun inT1(){}
}

interface T2 {
    fun inT2(){}
}

fun T1.forT1(){}
fun T2.forT2(){}
fun T1?.forNullableT1(){}
fun T2?.forNullableT2(){}
fun Any.forAny(){}
fun Any?.forNullableAny(){}

fun foo(o: T1?) {
    if (o is T2) {
        o.<caret>
    }
}

// EXIST: { lookupString: "inT1", attributes: "bold", icon: "nodes/method.svg"}
// EXIST: { lookupString: "inT2", attributes: "bold", icon: "nodes/method.svg"}
// EXIST: { lookupString: "hashCode", attributes: "", icon: "nodes/method.svg"}
// EXIST: { lookupString: "forT1", attributes: "bold", icon: "nodes/function.svg"}
// EXIST: { lookupString: "forT2", attributes: "bold", icon: "nodes/function.svg"}
// EXIST: { lookupString: "forNullableT1", attributes: "", icon: "nodes/function.svg"}
// EXIST: { lookupString: "forNullableT2", attributes: "", icon: "nodes/function.svg"}
// EXIST: { lookupString: "forAny", attributes: "", icon: "nodes/function.svg"}
// EXIST: { lookupString: "forNullableAny", attributes: "", icon: "nodes/function.svg"}
