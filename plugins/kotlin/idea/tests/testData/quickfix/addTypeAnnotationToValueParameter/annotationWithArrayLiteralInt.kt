// "Add type 'IntArray' to parameter 'value'" "true"
// LANGUAGE_VERSION: 1.7
// K2_ERROR: VALUE_PARAMETER_WITHOUT_EXPLICIT_TYPE

annotation class CollectionDefault(val value = [1, 2]<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddTypeAnnotationToValueParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddTypeAnnotationToValueParameterFixFactory$AddTypeAnnotationToValueParameterFix