// "Add type 'Int' to parameter 'value'" "true"

class CollectionDefault(vararg val value = intArrayOf(1, 2)<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddTypeAnnotationToValueParameterFix