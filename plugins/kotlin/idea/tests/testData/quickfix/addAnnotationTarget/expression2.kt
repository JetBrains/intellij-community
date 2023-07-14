// "Add annotation target" "true"
annotation class Foo

fun test() {
    var v = 0
    <caret>@Foo v++
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix