annotation class AnnParam

annotation class AnnProperty

abstract class WithComposedModifiers {
    @AnnProperty
    open var x: Array<out String> = emptyArray()

    constructor<caret>(@AnnParam vararg x: String) {
        this.x = x
    }
}