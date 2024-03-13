fun foo(index: Int, firstName: String = "John", lastName: String = "Smith") {

}

val f = foo(/*<# [namedParameters.kt:8]index|: #>*/0, lastName = "Johnson")
val f2 = foo(/*<# [namedParameters.kt:8]index|: #>*/0, firstName = "Joe", /*<# [namedParameters.kt:48]lastName|: #>*/"Johnson")