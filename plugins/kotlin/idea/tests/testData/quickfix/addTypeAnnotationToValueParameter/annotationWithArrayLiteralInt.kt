// "Add type 'IntArray' to parameter 'value'" "true"
// LANGUAGE_VERSION: 1.7

annotation class CollectionDefault(val value = [1, 2]<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddTypeAnnotationToValueParameterFix