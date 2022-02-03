// "Move '@SomeOptInAnnotation' annotation from getter to property" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@RequiresOptIn
annotation class SomeOptInAnnotation

@get:SomeOptInAnnotation<caret>
val someProperty: Int = 5
