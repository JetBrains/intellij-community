// "Remove forbidden opt-in annotation targets" "true"
// K2_ERROR: Opt-in requirement marker annotation cannot be used on the following code elements: type usage.

@Target(Annotati<caret>onTarget.TYPE, AnnotationTarget.TYPEALIAS, AnnotationTarget.FUNCTION)
@RequiresOptIn
annotation class Foo

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWrongOptInAnnotationTargetFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWrongOptInAnnotationTargetFix