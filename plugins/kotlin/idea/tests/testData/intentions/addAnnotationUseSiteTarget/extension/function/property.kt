// NO_OPTION: PROPERTY


@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class C

class Extension

fun @C<caret> Extension.foo(): String = ""
