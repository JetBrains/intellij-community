// PRIORITY: HIGH
// INTENTION_TEXT: "Import members from 'JavaUtilClass'"
// WITH_STDLIB
// ERROR: None of the following functions can be called with the arguments supplied: <br>public open fun overloadedMethod(i: Int): Unit defined in JavaUtilClass<br>public open fun overloadedMethod(i: String!): Unit defined in JavaUtilClass
// ERROR: None of the following functions can be called with the arguments supplied: <br>public open fun overloadedMethod(i: Int): Unit defined in JavaUtilClass<br>public open fun overloadedMethod(i: String!): Unit defined in JavaUtilClass
// ERROR: Unresolved reference: unresolved
// AFTER-WARNING: Variable 'bottom' is never used
// K2_ERROR: None of the following candidates is applicable:<br>static fun overloadedMethod(i: Int): Unit<br>static fun overloadedMethod(i: String!): Unit
// K2_ERROR: None of the following candidates is applicable:<br>static fun overloadedMethod(i: Int): Unit<br>static fun overloadedMethod(i: String!): Unit
// K2_ERROR: Unresolved reference 'unresolved'.
// K2_AFTER_ERROR: None of the following candidates is applicable:<br>static fun overloadedMethod(i: Int): Unit<br>static fun overloadedMethod(i: String!): Unit
// K2_AFTER_ERROR: None of the following candidates is applicable:<br>static fun overloadedMethod(i: Int): Unit<br>static fun overloadedMethod(i: String!): Unit
// K2_AFTER_ERROR: Unresolved reference 'unresolved'.

fun foo() {
    <caret>JavaUtilClass.overloadedMethod()

    val bottom = JavaUtilClass.STATIC_FIELD

    JavaUtilClass.overloadedMethod()

    JavaUtilClass.unresolved
}
