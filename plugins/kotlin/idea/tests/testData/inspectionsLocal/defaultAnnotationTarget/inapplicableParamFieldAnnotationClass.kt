// PROBLEM: none
// LANGUAGE_VERSION: 2.0

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
annotation class ParamField

annotation class Your(<caret>@ParamField val s: String)
