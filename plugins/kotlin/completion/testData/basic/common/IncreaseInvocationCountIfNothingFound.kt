object A {
    fun veryLongPrefixNotFounAnywhere() {}
}

fun test() {
    veryLongPrefixNotFounAnywh<caret>
}

// INVOCATION_COUNT: 1
// EXIST: veryLongPrefixNotFounAnywhere