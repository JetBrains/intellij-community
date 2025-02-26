// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
annotation class ExperimentalEncodingApi

@ExperimentalEncodingApi
class Base64 {
    companion object {
        fun encode(source: ByteArray): String = ""
    }
}

@OptIn(ExperimentalEncodingApi::class)<caret>
fun String.toBase64() = Base64.encode(toByteArray())