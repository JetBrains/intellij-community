// "Remove 'get:' target to make the annotation effective (changes semantics, see: https://youtrack.jetbrains.com/issue/KT-48141)" "false"
// ERROR: '@set:' annotations could be applied only to property declarations
// ERROR: This annotation is not applicable to target 'getter' and use site target '@set'
// WITH_STDLIB
class Foo {
    private var bar = 0
        <caret>@set/*comment*/:Deprecated("", level = DeprecationLevel.ERROR) get
}
