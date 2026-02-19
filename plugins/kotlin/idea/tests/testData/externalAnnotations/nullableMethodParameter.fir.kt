fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    x.methodWithNotNullParameter(<warning descr="[TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS] Java type mismatch: inferred type is 'Nothing?', but 'Int' was expected.">null</warning>)
}