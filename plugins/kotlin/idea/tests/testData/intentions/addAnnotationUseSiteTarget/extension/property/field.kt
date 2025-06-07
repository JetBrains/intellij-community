// NO_OPTION: FIELD|Add use-site target 'field'
// CHOSEN_OPTION: RECEIVER|Add use-site target 'receiver'

@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class C

class Extension

val @C<caret> Extension.bar: String
    get() = ""