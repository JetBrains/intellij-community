@Suppress("UNUSED_VARIABLE")
fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    val y: String = <error descr="[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected 'kotlin.String', actual 'kotlin.String?'." textAttributesKey="ERRORS_ATTRIBUTES">x.nullableField</error>
}