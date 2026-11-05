// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false

class KotlinImpl : JavaBase() {
    override fun process(<caret>input: String) {}
}