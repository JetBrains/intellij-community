// "Remove supertype" "true"
// WITH_STDLIB
// K2_ERROR: VALUE_CLASS_CANNOT_EXTEND_IDENTITY_CLASSES
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses
open class IdentityClass

value class ExtendsIdentity(val value: Int) : <caret>IdentityClass()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveSupertypeFix
