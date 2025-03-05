// "Remove 'get:' to make the annotation effective. (changes semantics. See: https://youtrack.jetbrains.com/issue/KT-48141)" "false"
// WITH_STDLIB
// ERROR: '@get:' annotations could be applied only to property declarations
// ERROR: This annotation is not applicable to target 'class' and use site target '@get'
// K2_AFTER_ERROR: '@get:' annotations can only be applied to property declarations.
// K2_AFTER_ERROR: This annotation is not applicable to target 'class' and use-site target '@get'. Applicable targets: class, function, property, annotation class, constructor, setter, getter, typealias
<caret>@get:Deprecated("", level = DeprecationLevel.ERROR)
class Foo
