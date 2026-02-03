// NO_OPTION: Add use-site target 'all'
// CHOSEN_OPTION: RECEIVER|Add use-site target 'receiver'
// COMPILER_ARGUMENTS: -Xannotation-target-all

@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class C

class Extension

val @C<caret> Extension.bar: String
    get() = ""