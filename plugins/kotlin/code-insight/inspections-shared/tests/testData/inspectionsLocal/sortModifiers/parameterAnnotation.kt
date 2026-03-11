// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// ERROR: Unresolved reference: a
// AFTER_ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// AFTER_ERROR: Unresolved reference: a
// K2_ERROR:
// K2_AFTER_ERROR:
// PROBLEM: none
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
import kotlin.annotation.AnnotationTarget.TYPE

@Target(VALUE_PARAMETER)
annotation class AnnotationWithValueTarget

@Target(TYPE)
annotation class AnnotationWithTypeTarget

context(@AnnotationWithValueTarget a: @AnnotationWithTypeTarget String)
<caret>fun foo(): String {
    return a
}