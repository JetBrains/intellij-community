// MODE: inheritors

abstract class SomeClass {/*<# [5+ Implementations] #>*/
open fun someFun() = ""/*<# [1 Override] #>*/
    abstract fun someAbstractFun()/*<# [5+ Implementations] #>*/
}

class DerivedClassA : SomeClass {
    override fun someFun() = "overridden"
    override fun someAbstractFun() = "overridden"
}
class DerivedClassB : SomeClass {
    override fun someAbstractFun() = "overridden"
}
class DerivedClassB1 : SomeClass {
    override fun someAbstractFun() = "overridden"
}
class DerivedClassB2 : SomeClass {
    override fun someAbstractFun() = "overridden"
}
class DerivedClassB3 : SomeClass {
    override fun someAbstractFun() = "overridden"
}
class DerivedClassB4 : SomeClass {
    override fun someAbstractFun() = "overridden"
}
class DerivedClassB5 : SomeClass {
    override fun someAbstractFun() = "overridden"
}
