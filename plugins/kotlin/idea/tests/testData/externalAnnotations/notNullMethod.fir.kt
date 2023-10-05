fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    x.notNullMethod()<warning descr="[UNNECESSARY_SAFE_CALL] Unnecessary safe call on a non-null receiver of type 'kotlin.String'." textAttributesKey="WARNING_ATTRIBUTES">?.</warning>foo()
}

fun String.foo() {

}
