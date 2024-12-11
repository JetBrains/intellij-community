// NO_OPTION: PROPERTY_GETTER

@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class C

class Extension

val @C<caret> Extension.bar: String
    get() = ""