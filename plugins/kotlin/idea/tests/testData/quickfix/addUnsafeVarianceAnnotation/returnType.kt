// "Add '@UnsafeVariance' annotation" "true"
// K2_ERROR: TYPE_VARIANCE_CONFLICT_ERROR
interface Foo<in E> {
    fun bar(): E<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix