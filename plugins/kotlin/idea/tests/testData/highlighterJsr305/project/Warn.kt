import foo.A

fun main(a: A) {
    <warning descr="[RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS]">a.field</warning>.length
}
