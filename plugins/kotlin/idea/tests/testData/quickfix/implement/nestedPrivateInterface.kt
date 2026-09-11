// "Implement interface" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
// WITH_STDLIB

class Container {
    private interface <caret>Base {
        var z: Double
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.intentions.CreateKotlinSubClassIntention