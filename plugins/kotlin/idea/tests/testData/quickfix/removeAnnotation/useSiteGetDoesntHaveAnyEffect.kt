// "Remove annotation, since it has no effect. See: https://youtrack.jetbrains.com/issue/KT-48141" "true"
// WITH_STDLIB
class Foo {
    private val bar = 0
        <caret>@get:Deprecated("", level = DeprecationLevel.ERROR) get
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix