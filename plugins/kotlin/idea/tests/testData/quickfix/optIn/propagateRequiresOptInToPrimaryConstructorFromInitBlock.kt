// "Propagate 'Ann' opt-in requirement to constructor" "false"
// K2_ERROR: OPT_IN_USAGE_ERROR
// K2_AFTER_ERROR: OPT_IN_USAGE_ERROR
// KTIJ-40756

@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class Ann

@Ann fun annFun() {}

class C1 : Any() {
    init {
        <caret>annFun()
    }
}
