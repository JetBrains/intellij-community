// "Change function signature to 'fun foo(a: Int, b: String): Any?'" "true"
interface  A {
    public fun foo(a: Int = 1, b: String = "str"): Any?
}

class B : A {
    public override<caret> fun foo(a: Int): Any? = null
}