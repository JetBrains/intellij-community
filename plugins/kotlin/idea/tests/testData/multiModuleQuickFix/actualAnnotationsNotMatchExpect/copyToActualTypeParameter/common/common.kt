// DISABLE-ERRORS
@Target(AnnotationTarget.TYPE_PARAMETER)
annotation class Ann

expect fun <@Ann T> foo()
