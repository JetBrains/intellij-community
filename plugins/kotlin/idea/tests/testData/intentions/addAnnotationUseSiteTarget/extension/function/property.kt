// NO_OPTION: PROPERTY|Add use-site target 'property'
// CHOSEN_OPTION: RECEIVER|Add use-site target 'receiver'

@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class C

class Extension

fun @C<caret> Extension.foo(): String = ""
