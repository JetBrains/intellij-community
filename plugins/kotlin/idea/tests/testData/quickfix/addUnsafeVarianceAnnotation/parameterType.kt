// "Add '@UnsafeVariance' annotation" "true"
interface Foo<out E> {
    fun bar(e: E<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix