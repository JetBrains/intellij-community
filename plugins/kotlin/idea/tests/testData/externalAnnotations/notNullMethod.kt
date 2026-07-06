// FIR_IDENTICAL
fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    x.notNullMethod()<warning descr="[UNNECESSARY_SAFE_CALL]">?.</warning>foo()
}

fun String.foo() {

}
