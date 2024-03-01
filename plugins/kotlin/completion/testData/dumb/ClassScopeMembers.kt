class Test {
    fun prefixFun() {

    }

    fun prefixVal = 1

    fun someFunction() {
        val a = test.prefix<caret>
    }
}

// EXIST: prefixFun, prefixVal
// NOTHING_ELSE