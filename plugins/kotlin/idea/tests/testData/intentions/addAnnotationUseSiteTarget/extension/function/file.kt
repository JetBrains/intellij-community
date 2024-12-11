// NO_OPTION: FILE
// CHOSEN_OPTION: VALUE_PARAMETER

@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class C

class Extension

fun @C<caret> Extension.foo(): String = ""
