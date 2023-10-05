fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    x.methodWithNotNullParameter(<error descr="[NULL_FOR_NONNULL_TYPE] Null cannot be a value of a non-null type 'kotlin.Int'." textAttributesKey="ERRORS_ATTRIBUTES">null</error>)
}