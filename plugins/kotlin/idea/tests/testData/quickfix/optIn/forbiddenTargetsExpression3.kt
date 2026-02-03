// "Remove forbidden opt-in annotation targets" "true"

@Target(Annotati<caret>onTarget.TYPE, AnnotationTarget.TYPEALIAS, AnnotationTarget.FUNCTION)
@RequiresOptIn
annotation class Foo

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWrongOptInAnnotationTargetFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWrongOptInAnnotationTargetFix