object A {
    fun veryLongPrefixNotFounAnywhere() {}
}

val veryLongPrefixNotFounAnywhere2: Int = 5

fun test() {
    veryLongPrefixNotFounAnywh<caret>
}

// INVOCATION_COUNT: 1
// ABSENT: veryLongPrefixNotFounAnywhere
// EXIST: veryLongPrefixNotFounAnywhere2