// "Remove annotation, since it has no effect. See https://youtrack.jetbrains.com/issue/KT-48141" "false"
// IGNORE_K2
// ACTION: Create test
// ACTION: Make internal
// ACTION: Make private
// WITH_STDLIB
// ERROR: '@get:' annotations could be applied only to property declarations
// ERROR: This annotation is not applicable to target 'class' and use site target '@get'
<caret>@get:Deprecated("", level = DeprecationLevel.ERROR)
class Foo
