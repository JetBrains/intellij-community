// "Implement interface" "true"
// WITH_STDLIB
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

class Container {
    interface <caret>Base {
        val x: Boolean

        val y: Double
            get() = 3.14

        fun foo(): String = ""

        fun bar(z: Int): String
    }
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.CreateKotlinSubClassIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.CreateKotlinSubClassIntention