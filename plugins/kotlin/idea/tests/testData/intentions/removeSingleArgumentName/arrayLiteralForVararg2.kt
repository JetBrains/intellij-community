// IS_APPLICABLE: false
annotation class A(vararg val s: String, val i: Int)

@A(<caret>s = ["foo", "bar"], 1)
class C