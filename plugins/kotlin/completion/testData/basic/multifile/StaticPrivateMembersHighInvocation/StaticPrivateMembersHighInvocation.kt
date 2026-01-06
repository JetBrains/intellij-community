package bar

fun buz() {
    // Completion gets rerun with invocation count 2, but we still do not want to show private members
    somePrefix<caret>
}

// INVOCATION_COUNT: 2
// EXIST: somePrefixFun
// EXIST: somePrefixVal
// EXIST: somePrefixConst