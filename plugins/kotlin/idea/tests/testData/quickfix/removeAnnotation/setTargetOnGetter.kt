// "Remove @JvmOverloads annotation" "false"
// ERROR: '@set:' annotations could be applied only to property declarations
// ERROR: This annotation is not applicable to target 'getter' and use site target '@set'
// K2_AFTER_ERROR: '@set:' annotations can only be applied to property declarations.
// K2_AFTER_ERROR: This annotation is not applicable to target 'getter' and use-site target '@set'. Applicable targets: class, function, property, annotation class, constructor, setter, getter, typealias
// WITH_STDLIB

class Foo {
    private var bar = 0
        <caret>@set/*comment*/:Deprecated("", level = DeprecationLevel.ERROR) get
}
