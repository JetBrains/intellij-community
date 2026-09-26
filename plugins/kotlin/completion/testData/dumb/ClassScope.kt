class Test {
    fun prefixFun() {

    }

    fun prefixVal = 1

    fun someFunction() {
        val a = prefix<caret>
    }
}

// EXIST: prefixFun, prefixVal
// NOTHING_ELSE