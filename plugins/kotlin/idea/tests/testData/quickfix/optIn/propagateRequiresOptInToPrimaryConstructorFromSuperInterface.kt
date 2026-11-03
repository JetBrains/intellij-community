// "Propagate 'Wide' opt-in requirement to constructor" "false"
// K2_ERROR: OPT_IN_USAGE_ERROR
// K2_AFTER_ERROR: OPT_IN_USAGE_ERROR
// KTIJ-40756

@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class Wide

@Wide interface IWide

class C2 : Any(), IWide<caret>
