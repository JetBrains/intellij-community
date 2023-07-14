// "Move 'SomeOptInAnnotation' opt-in requirement from getter to property" "true"

@RequiresOptIn
annotation class SomeOptInAnnotation

@get:SomeOptInAnnotation<caret>
val someProperty: Int = 5
