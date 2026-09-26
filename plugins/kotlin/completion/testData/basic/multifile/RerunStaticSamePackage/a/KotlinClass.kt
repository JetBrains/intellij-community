package a

class KotlinClass {
    companion object {
        const val somePrefixKotlinStaticFieldA = "staticField"
        fun somePrefixKotlinStaticMethodA() = "staticMethod"
        internal const val somePrefixKotlinStaticFieldB = "staticField"
        internal fun somePrefixKotlinStaticMethodB() = "staticMethod"
        private const val somePrefixKotlinStaticFieldC = "staticField"
        private fun somePrefixKotlinStaticMethodC() = "staticMethod"
    }
}

// ALLOW_AST_ACCESS
