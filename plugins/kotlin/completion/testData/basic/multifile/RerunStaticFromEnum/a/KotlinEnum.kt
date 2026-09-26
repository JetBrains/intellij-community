package a

enum class KotlinEnum {
    A, B;

    companion object {
        const val somePrefixKotlinStaticField = "staticField"
        fun somePrefixKotlinStaticMethod() = "staticMethod"
    }
    
    val somePrefixKotlinInstanceField = "instanceField"
    fun somePrefixKotlinInstanceMethod() = "instanceMethod"
}

// ALLOW_AST_ACCESS
