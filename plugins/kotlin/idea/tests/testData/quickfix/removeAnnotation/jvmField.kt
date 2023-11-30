// "Remove @JvmField annotation" "true"
// IGNORE_K2
// WITH_STDLIB
class Foo {
    <caret>@JvmField private val bar = 0
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix