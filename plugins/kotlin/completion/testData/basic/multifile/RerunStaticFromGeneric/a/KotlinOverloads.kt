package a

class KotlinOverloads {
    companion object {
        const val somePrefixKotlinStaticField = "staticField"
        fun <T> somePrefixKotlinStaticMethod(t: T): T = t
    }
    
    val somePrefixKotlinInstanceField = "instanceField"
    fun <T> somePrefixKotlinInstanceMethod(t: T): T = t
}

// ALLOW_AST_ACCESS
