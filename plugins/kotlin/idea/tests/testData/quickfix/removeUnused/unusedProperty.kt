// "Safe delete 'myOwnProperty96'" "true"
class UnusedProperty() {
    val <caret>myOwnProperty96: String = ":)"
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.highlighting.SafeDeleteFix