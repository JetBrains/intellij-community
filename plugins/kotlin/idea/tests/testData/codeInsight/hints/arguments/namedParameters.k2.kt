fun foo(index: Int, firstName: String = "John", lastName: String = "Smith") {}
fun simpleFun1(name: String){}
fun simpleFun2(name: String){}
fun simpleFun2(name: String, value: Int){}

val f = foo(/*<# [namedParameters.kt:8]index| = #>*/0, lastName = "Johnson")
val f2 = foo(/*<# [namedParameters.kt:8]index| = #>*/0, firstName = "Joe", /*<# [namedParameters.kt:48]lastName| = #>*/"Johnson")
fun m() {
    val myName = "name"
    val name = "name"
    val n = "name"
    simpleFun0()
    simpleFun1(/*<# [namedParameters.kt:94]name| = #>*/"name")
    simpleFun1(name)
    simpleFun1(/*<# [namedParameters.kt:94]name| = #>*/n)
    simpleFun2(name)
    simpleFun2(myName)
    simpleFun2(/*<# [namedParameters.kt:125]name| = #>*/n)
}