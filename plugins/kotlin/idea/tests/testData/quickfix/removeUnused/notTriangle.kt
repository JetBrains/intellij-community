// "Safe delete 'something'" "true"

abstract class Abstract {
    open fun <caret>something() = "hi"
}

class Test: Abstract() {
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.highlighting.SafeDeleteFix