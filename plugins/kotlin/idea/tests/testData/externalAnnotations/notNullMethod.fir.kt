fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    x.notNullMethod()?.foo()
}

fun String.foo() {

}
