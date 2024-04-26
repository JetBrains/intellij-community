class Test {
    constructor(prefixTest: Int) {

    }

    fun test() = prefix<caret>
}

// ABSENT: prefixTest
// NOTHING_ELSE