package bar

fun buz() {
    // Completion gets rerun with invocation count 2, but we still do not want to show private members
    somePrefix<caret>
}

// INVOCATION_COUNT: 1
// ABSENT: somePrefixFun
// ABSENT: somePrefixVal
// ABSENT: somePrefixConst
