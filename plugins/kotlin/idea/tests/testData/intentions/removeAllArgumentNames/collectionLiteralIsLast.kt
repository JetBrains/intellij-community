annotation class A(val i: Int, val j: Int, vararg val s: String)

@A(<caret>i = 1, j = 2, s = ["foo", "bar"])
class C
