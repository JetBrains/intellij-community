package a

class KotlinClass(val somePrefixKotlinInstanceFieldA: String) {
    companion object {
        const val somePrefixKotlinStaticField = "staticField"
        fun somePrefixKotlinStaticMethod() = "staticMethod"
    }

    val somePrefixKotlinInstanceFieldB = "instanceField"
    fun somePrefixKotlinInstanceMethod() = "instanceMethod"
}

// ALLOW_AST_ACCESS
