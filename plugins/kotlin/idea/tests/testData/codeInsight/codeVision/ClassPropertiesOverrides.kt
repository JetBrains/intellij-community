// MODE: inheritors

abstract class SomeClass {/*<# [2 Implementations] #>*/
abstract val someAbstractProperty: Int/*<# [1 Implementation] #>*/
    open val nonAbstractProperty: Int = 10/*<# [2 Overrides] #>*/
    open val notToBeOverriddenProperty: Int = 10
}

open class DerivedClassA : SomeClass() {/*<# [1 Inheritor] #>*/
override val someAbstractProperty: Int = 5
    override val nonAbstractProperty: Int = 15 // NOTE that DerivedClassB overrides both getter and setter but counted once/*<# [1 Override] #>*/
}

class DerivedClassB : DerivedClassA() {
    override var nonAbstractProperty: Int = 15
        get() = 20
        set(value) {field = value / 2}
}