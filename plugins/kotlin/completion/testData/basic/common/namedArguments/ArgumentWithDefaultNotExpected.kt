// FIR_COMPARISON
// FIR_IDENTICAL
fun multipleBooleanArguments(flag1: Boolean, flag2: Boolean) {}
fun test() {
    multipleBooleanArguments(f<caret>)
}

// WITH_ORDER
// EXIST: "flag1 ="
// EXIST: "flag1 = false"
// EXIST: "flag1 = true"
// EXIST: "flag2 ="
// ABSENT: "flag2 = false"
// ABSENT: "flag2 = true"
