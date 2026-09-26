package a

class KotlinOuter {
    class Nested {
        companion object {
            const val somePrefixKotlinStaticField = "staticField"
            fun somePrefixKotlinStaticMethod() = "staticMethod"
        }
        
        val somePrefixKotlinInstanceField = "instanceField"
        fun somePrefixKotlinInstanceMethod() = "instanceMethod"
    }
}

// ALLOW_AST_ACCESS
