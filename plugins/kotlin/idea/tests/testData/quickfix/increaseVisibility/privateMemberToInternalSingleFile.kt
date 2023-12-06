// "Make 'x' internal" "true"
class First(private val x: Int)

class Second(f: First) {
    val y = f.<caret>x
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToInternalFix