// "Add '@UnsafeVariance' annotation" "true"
interface Foo<in E> {
    fun bar(): E<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix