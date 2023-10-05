@Suppress("UNUSED_VARIABLE")
fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    val y: String = <error descr="[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected 'kotlin.String', actual 'kotlin.String?'." textAttributesKey="ERRORS_ATTRIBUTES"><error descr="[TYPE_MISMATCH] Type mismatch: inferred type is 'kotlin.String?', but 'kotlin.String' was expected." textAttributesKey="ERRORS_ATTRIBUTES">x.nullableMethod()</error></error>
}