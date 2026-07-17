fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    x.methodWithNotNullParameter(<warning descr="[TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS]">null</warning>)
}