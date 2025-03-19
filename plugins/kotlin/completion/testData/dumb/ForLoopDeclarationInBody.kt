fun test() {
    for (prefixTest in test) {
        val a = prefix<caret>
    }
}

// EXIST: prefixTest
// NOTHING_ELSE