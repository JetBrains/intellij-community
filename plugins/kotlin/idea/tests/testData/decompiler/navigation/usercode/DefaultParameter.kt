import testData.libraries.*

fun test() {
    funWithDefaultParameter(42)
    funWithDefaultParameter(42, "awd")
    val o = OpenClassWithFunctionWithDefaultParameter()
    o.doSmth()
    o.doSmth(false)

    val c = ChildOfOpenClassWithFunctionWithDefaultParameter()
    c.doSmth()
    c.doSmth(false)
}