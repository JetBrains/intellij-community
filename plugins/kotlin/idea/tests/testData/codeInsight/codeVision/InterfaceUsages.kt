// MODE: usages

interface SomeInterface {}/*<# [5 Usages] #>*/
interface SomeOtherInterface : SomeInterface {} // <== (1): interface extension
class SomeClass : SomeInterface { // <== (2): interface implementation
    fun acceptsInterface(param: SomeInterface) {} // <== (3): parameter type/*<# [1 Usage] #>*/
    fun returnsInterface(): SomeInterface {} // <== (4): return type
    fun main() = acceptsInterface(object : SomeInterface {}) // <== (5): anonymous class instance
}