// "Implement sealed class" "true"
// WITH_STDLIB
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

sealed class <caret>Base {
    abstract fun foo(): Int

    class BaseImpl : Base() {
        override fun foo() = throw UnsupportedOperationException()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.CreateKotlinSubClassIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.CreateKotlinSubClassIntention