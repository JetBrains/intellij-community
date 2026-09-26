// PRIORITY: HIGH
// INTENTION_TEXT: "Import members from 'JavaUtilClass'"
// WITH_STDLIB
// ERROR: None of the following functions can be called with the arguments supplied: <br>public open fun overloadedMethod(i: Int): Unit defined in JavaUtilClass<br>public open fun overloadedMethod(i: String!): Unit defined in JavaUtilClass
// ERROR: None of the following functions can be called with the arguments supplied: <br>public open fun overloadedMethod(i: Int): Unit defined in JavaUtilClass<br>public open fun overloadedMethod(i: String!): Unit defined in JavaUtilClass
// ERROR: Unresolved reference: unresolved
// AFTER-WARNING: Variable 'bottom' is never used
// K2_AFTER_ERROR: NONE_APPLICABLE
// K2_AFTER_ERROR: NONE_APPLICABLE
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: NONE_APPLICABLE
// K2_ERROR: NONE_APPLICABLE
// K2_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: UNRESOLVED_REFERENCE

fun foo() {
    <caret>JavaUtilClass.overloadedMethod()

    val bottom = JavaUtilClass.STATIC_FIELD

    JavaUtilClass.overloadedMethod()

    JavaUtilClass.unresolved
}
