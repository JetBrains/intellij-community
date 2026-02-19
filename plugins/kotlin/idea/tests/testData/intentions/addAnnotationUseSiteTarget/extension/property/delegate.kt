// NO_OPTION: PROPERTY_DELEGATE_FIELD|Add use-site target 'delegate'
// CHOSEN_OPTION: RECEIVER|Add use-site target 'receiver'

@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class C

class Extension

val @C<caret> Extension.bar: String
    get() = ""