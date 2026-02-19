annotation class A(vararg val s: String, val i: Int, val j: Int)

@A(<caret>s = ["foo", "bar"], i = 1, j = 2)
class C