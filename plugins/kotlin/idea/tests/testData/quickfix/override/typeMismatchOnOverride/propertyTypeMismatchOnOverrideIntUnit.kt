// "Change type to 'Int'" "true"
// K2_ERROR: ABSTRACT_PROPERTY_WITHOUT_TYPE
abstract class A {
    abstract var x : Int
}

abstract class B : A() {
    override abstract var x<caret>
}

// In K2, this fix works if a type of `x` is defined, for example, see propertyTypeMismatchOnOverrideIntLong.kt
// IGNORE_K2
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix$OnType