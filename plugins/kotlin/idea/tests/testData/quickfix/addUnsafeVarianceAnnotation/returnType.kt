// "Add '@UnsafeVariance' annotation" "true"
// K2_ERROR: Type parameter 'E' is declared as 'in' but occurs in 'out' position in type 'E (of interface Foo<in E>)'.
interface Foo<in E> {
    fun bar(): E<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix