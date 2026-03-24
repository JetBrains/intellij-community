// "Add type 'Int' to parameter 'value'" "true"
// LANGUAGE_VERSION: 1.7
// K2_ERROR: An explicit type is required on a value parameter.
// K2_ERROR: Invalid type of annotation member.

annotation class CollectionDefault(vararg val value = [1, 2]<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddTypeAnnotationToValueParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddTypeAnnotationToValueParameterFixFactory$AddTypeAnnotationToValueParameterFix