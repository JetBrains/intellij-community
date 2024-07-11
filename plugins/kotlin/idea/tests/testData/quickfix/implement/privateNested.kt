// "Create subclass" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

class Container {
    private open class <caret>Base {
        open var x: String = ""

        open fun foo(): String = ""
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.CreateKotlinSubClassIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.CreateKotlinSubClassIntention