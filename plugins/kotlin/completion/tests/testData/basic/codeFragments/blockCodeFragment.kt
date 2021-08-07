fun foo() {
    val aaabbbccc = 1
    aaa<caret>bbbccc
}

// BLOCK_CODE_FRAGMENT
// INVOCATION_COUNT: 1
// EXIST: aaabbbccc, aaabbcc