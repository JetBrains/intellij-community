// "Make '<init>' public" "true"

private class Marker private constructor()

fun foo(): Any {
    return <caret>Marker()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicFix