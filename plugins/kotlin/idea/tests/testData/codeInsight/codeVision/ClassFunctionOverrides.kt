// MODE: inheritors

abstract class SomeClass {/*<# [2 Implementations] #>*/
open fun someFun() = ""/*<# [1 Override] #>*/
    abstract fun someAbstractFun()/*<# [2 Implementations] #>*/
}

class DerivedClassA : SomeClass {
    override fun someFun() = "overridden"
    override fun someAbstractFun() = "overridden"
}
class DerivedClassB : SomeClass {
    override fun someAbstractFun() = "overridden"
}
