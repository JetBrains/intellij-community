// "Replace deprecated '@Experimental' annotation with '@RequiresOptIn'" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@Experimental<caret>
@Target(AnnotationTarget.TYPEALIAS)
annotation class Marker
