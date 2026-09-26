package a

annotation class KotlinAnno {
    companion object {
        const val somePrefixKotlinStaticField = "annoConst"
        fun somePrefixKotlinStaticMethod() = "staticMethod"
    }

    val somePrefixKotlinInstanceField = "instanceField"
    fun somePrefixKotlinInstanceMethod() = "instanceMethod"
}

// ALLOW_AST_ACCESS