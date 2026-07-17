@Suppress("UNUSED_VARIABLE")
fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    val y: String = <warning descr="[TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS]">x.nullableField</warning>
}