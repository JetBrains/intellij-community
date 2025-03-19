// "Remove 'get:' to make the annotation effective. (changes semantics. See: https://youtrack.jetbrains.com/issue/KT-48141)" "true"
// WITH_STDLIB
class Foo {
    private val bar = 0
        <caret>@get:Deprecated("", level = DeprecationLevel.ERROR) get
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUseSiteTargetFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUseSiteTargetFix