// NO_OPTION: PROPERTY_GETTER


@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class C

class Extension

fun @C<caret> Extension.foo(): String = ""
