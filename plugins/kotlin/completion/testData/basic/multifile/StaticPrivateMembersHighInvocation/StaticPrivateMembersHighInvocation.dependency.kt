package foo

class Bar {
    companion object {
        fun somePrefixFun() {}
        val somePrefixVal = 99
        const val somePrefixConst = 100
    }
}

// ALLOW_AST_ACCESS