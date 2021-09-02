// "Move 'SomeOptInAnnotation' opt-in requirement from getter to property" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_RUNTIME

@RequiresOptIn
annotation class SomeOptInAnnotation

@get:SomeOptInAnnotation<caret>
val someProperty: Int = 5
