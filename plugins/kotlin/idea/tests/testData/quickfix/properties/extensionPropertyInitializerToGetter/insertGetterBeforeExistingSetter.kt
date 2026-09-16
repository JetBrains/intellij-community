// "Convert extension property initializer to getter" "true"
// K2_ERROR: EXTENSION_PROPERTY_WITH_BACKING_FIELD
var String.foo: Int = 0<caret>
    set(value) {}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertPropertyInitializerToGetterFix