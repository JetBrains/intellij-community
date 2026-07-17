class A {
    internal companion object {
        class B {
            class C
            interface D
            companion object {}
        }
    }
}

fun <error descr="[EXPOSED_RECEIVER_TYPE]">A.Companion.B.C</error>.foo() {}

interface E : <error descr="[EXPOSED_SUPER_INTERFACE]">A.Companion.B.D</error>

val <error descr="[EXPOSED_PROPERTY_TYPE]">x</error> = A.Companion.B

class F<<error descr="[EXPOSED_TYPE_PARAMETER_BOUND]">T : <warning descr="[FINAL_UPPER_BOUND]">A.Companion.B</warning></error>>(val x: T)



