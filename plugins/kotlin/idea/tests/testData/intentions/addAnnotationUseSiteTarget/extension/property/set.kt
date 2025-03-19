// NO_OPTION: PROPERTY_SETTER|Add use-site target 'set'
// CHOSEN_OPTION: RECEIVER|Add use-site target 'receiver'

@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class C

class Extension

val @C<caret> Extension.bar: String
    get() = ""