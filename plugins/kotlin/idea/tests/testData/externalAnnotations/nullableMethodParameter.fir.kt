fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    x.methodWithNotNullParameter(<warning descr="[NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS] Java type mismatch: inferred type is 'kotlin.Int', but 'kotlin.Nothing?' was expected.">null</warning>)
}