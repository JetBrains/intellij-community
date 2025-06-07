fun foo(index: Int, firstName: String = "John", lastName: String = "Smith") {}
fun simpleFun1(name: String){}
fun simpleFun2(name: String){}
fun simpleFun2(name: String, value: Int){}
val f = foo(<hint text="index:"/>0, lastName = "Johnson")
val f2 = foo(<hint text="index:"/>0, firstName = "Joe", <hint text="lastName:"/>"Johnson")
fun fVararg(vararg v: String) = fVararg(*v)
fun m() {
    val myName = "name"
    val name = "name"
    val n = "name"
    simpleFun0()
    simpleFun1(<hint text="name:"/>"name")
    simpleFun1(name)
    simpleFun1(n)
    simpleFun2(name)
    simpleFun2(myName)
    simpleFun2(n)
}