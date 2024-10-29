// "Implement abstract class" "true"
// WITH_STDLIB
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

class Container {
    inner abstract class <caret>Base {
        abstract fun foo(): String
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.CreateKotlinSubClassIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.CreateKotlinSubClassIntention