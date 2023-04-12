// "Remove annotation, since it doesn't have any effect. See https://youtrack.jetbrains.com/issue/KT-48141" "true"
// WITH_STDLIB
class Foo {
    private val bar = 0
        <caret>@get:Deprecated("", level = DeprecationLevel.ERROR) get
}
