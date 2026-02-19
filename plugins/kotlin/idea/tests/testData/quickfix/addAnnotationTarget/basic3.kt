// "Add annotation target" "true"

@Target()
annotation class Foo

<caret>@Foo
class Test
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix