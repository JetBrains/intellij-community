@Suppress("UNUSED_VARIABLE")
fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    val y: String = <warning descr="[NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS]">x.nullableMethod()</warning>
}