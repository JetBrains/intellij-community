// FIR_COMPARISON
// FIR_IDENTICAL
fun multipleNullableArguments(n1: Int?, n2: Int?) {}
fun test() {
    multipleNullableArguments(n<caret>)
}

// WITH_ORDER
// EXIST: "n1 ="
// EXIST: "n1 = null"
// EXIST: "n2 ="
// ABSENT: "n2 = null"
