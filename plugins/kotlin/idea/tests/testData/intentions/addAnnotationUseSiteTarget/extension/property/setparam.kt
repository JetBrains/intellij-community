// NO_OPTION: SETTER_PARAMETER|Add use-site target 'setparam'
// CHOSEN_OPTION: RECEIVER|Add use-site target 'receiver'

@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class C

class Extension

val @C<caret> Extension.bar: String
    get() = ""