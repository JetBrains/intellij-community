// MODE: usages

open class SomeClass {}/*<# [5 Usages] #>*/
class SomeOtherClass : SomeClass {} // <== (1): class extension
class SomeYetOtherClass : SomeClass { // <== (2): class extension
    fun acceptsClass(param: SomeClass) {} // <== (3): parameter type/*<# [1 Usage] #>*/
    fun returnsInterface(): SomeClass {} // <== (4): return type
    fun main() = acceptsClass(object : SomeClass {}) // <== (5): anonymous class instance
}