// DISABLE_ERRORS
@Target(AnnotationTarget.TYPE)
annotation class Ann

expect fun foo(p: @Ann Any)
