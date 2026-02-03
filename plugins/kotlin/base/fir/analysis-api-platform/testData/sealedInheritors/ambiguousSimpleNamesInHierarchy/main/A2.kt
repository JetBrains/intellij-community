package bar

sealed class A

class I1 : A()
open class I2 : A()

class IndirectInheritor1 : I2(), foo.A
class IndirectInheritor2 : I2()
