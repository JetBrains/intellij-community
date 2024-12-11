// NO_OPTION: PROPERTY

@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class C

class Extension

val @C<caret> Extension.bar: String
    get() = ""