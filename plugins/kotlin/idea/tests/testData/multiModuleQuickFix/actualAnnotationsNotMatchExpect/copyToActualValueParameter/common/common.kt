// DISABLE_ERRORS
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Ann

expect fun foo(@Ann p: Any)
