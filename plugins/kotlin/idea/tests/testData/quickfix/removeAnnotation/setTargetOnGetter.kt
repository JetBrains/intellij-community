// "Remove @JvmOverloads annotation" "false"
// ERROR: '@set:' annotations could be applied only to property declarations
// ERROR: This annotation is not applicable to target 'getter' and use site target '@set'
// WITH_STDLIB
// K2_ERROR: INAPPLICABLE_TARGET_ON_PROPERTY
// K2_ERROR: WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET
// K2_AFTER_ERROR: INAPPLICABLE_TARGET_ON_PROPERTY
// K2_AFTER_ERROR: WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET

class Foo {
    private var bar = 0
        <caret>@set/*comment*/:Deprecated("", level = DeprecationLevel.ERROR) get
}
