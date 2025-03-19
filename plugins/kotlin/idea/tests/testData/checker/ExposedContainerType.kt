class A {
    internal companion object {
        class B {
            class C
            interface D
            companion object {}
        }
    }
}

fun <error descr="[EXPOSED_RECEIVER_TYPE] 'public' member exposes its 'internal' receiver type containing declaration Companion">A.Companion.B.C</error>.foo() {}

interface E : <error descr="[EXPOSED_SUPER_INTERFACE] 'public' sub-interface exposes its 'internal' supertype containing declaration Companion">A.Companion.B.D</error>

val <error descr="[EXPOSED_PROPERTY_TYPE] 'public' property exposes its 'internal' type containing declaration Companion">x</error> = A.Companion.B

class F<<error descr="[EXPOSED_TYPE_PARAMETER_BOUND] 'public' generic exposes its 'internal' parameter bound type containing declaration Companion">T : <warning descr="[FINAL_UPPER_BOUND] 'A.Companion.B' is a final type, and thus a value of the type parameter is predetermined">A.Companion.B</warning></error>>(val x: T)



