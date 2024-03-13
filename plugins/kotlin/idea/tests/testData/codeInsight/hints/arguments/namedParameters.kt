fun foo(index: Int, firstName: String = "John", lastName: String = "Smith") {

}

val f = foo(<hint text="index:"/>0, lastName = "Johnson")
val f2 = foo(<hint text="index:"/>0, firstName = "Joe", <hint text="lastName:"/>"Johnson")