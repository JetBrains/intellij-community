// "Add annotation target" "true"

annotation class Foo

class Test {
    fun foo(): <caret>@Foo Int = 1
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix