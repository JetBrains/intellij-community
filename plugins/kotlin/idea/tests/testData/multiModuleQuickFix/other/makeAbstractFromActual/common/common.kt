interface IFoo { val a: Int }

expect open class Op() : IFoo

class OpChild : Op() { }
