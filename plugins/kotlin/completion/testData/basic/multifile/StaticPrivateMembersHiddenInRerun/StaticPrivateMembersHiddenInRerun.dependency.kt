package foo

class Bar {
    companion object {
        private fun somePrefixFun() {}
        private val somePrefixVal = 99
        private const val somePrefixConst = 100
        const val somePrefixConstOther = 100
    }
}

// ALLOW_AST_ACCESS