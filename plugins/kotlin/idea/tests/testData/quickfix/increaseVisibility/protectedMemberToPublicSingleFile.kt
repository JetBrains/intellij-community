// "Make 'x' public" "true"
class First(protected val x: Int)

class Second(f: First) {
    val y = f.<caret>x
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicFix