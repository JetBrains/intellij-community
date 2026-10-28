// "Opt in for 'X' on constructor" "false"
// K2_ERROR: OPT_IN_USAGE_ERROR
// K2_AFTER_ERROR: OPT_IN_USAGE_ERROR
// KTIJ-40645

@RequiresOptIn
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION)
annotation class X

open class B @X constructor()

class A @X internal constructor() : B() {
    init {
        <caret>f()
    }

    @X
    fun f() {}
}
