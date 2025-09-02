// "Remove @JvmField annotation" "true"
// WITH_STDLIB
class Foo {
    <caret>@JvmField private val bar = 0
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix