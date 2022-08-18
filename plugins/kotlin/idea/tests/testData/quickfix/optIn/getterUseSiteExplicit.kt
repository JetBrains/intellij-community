// "Move 'SomeOptInAnnotation' opt-in requirement from getter to property" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@RequiresOptIn
annotation class SomeOptInAnnotation

@get:SomeOptInAnnotation<caret>
val someProperty: Int = 5
