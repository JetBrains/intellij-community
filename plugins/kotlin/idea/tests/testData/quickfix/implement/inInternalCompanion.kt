// "Create subclass" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

class Container {
    internal companion object {
        open class <caret>Base
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.CreateKotlinSubClassIntention