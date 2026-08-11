// RUNTIME
<error descr="[WRONG_ANNOTATION_TARGET]">@JvmStatic</error>
class A {
    <error descr="[WRONG_ANNOTATION_TARGET]">@JvmStatic</error>
    companion object {
        @JvmStatic fun a1() {

        }
    }

    <error descr="[WRONG_ANNOTATION_TARGET]">@JvmStatic</error>
    object A {
        @JvmStatic fun a2() {

        }
    }

    fun test() {
        val <warning descr="[UNUSED_VARIABLE]">s</warning> = object {
            <error descr="[JVM_STATIC_NOT_IN_OBJECT_OR_COMPANION]">@JvmStatic fun a3()</error> {

            }
        }
    }

    <error descr="[JVM_STATIC_NOT_IN_OBJECT_OR_COMPANION]">@JvmStatic fun a4()</error> {

    }
}

<error descr="[WRONG_ANNOTATION_TARGET]">@JvmStatic</error>
interface B {
    companion object {
        @JvmStatic fun a1() {

        }
    }

    object A {
        @JvmStatic fun a2() {

        }
    }

    fun test() {
        val <warning descr="[UNUSED_VARIABLE]">s</warning> = object {
            <error descr="[JVM_STATIC_NOT_IN_OBJECT_OR_COMPANION]">@JvmStatic fun a3()</error> {

            }
        }
    }

    <error descr="[JVM_STATIC_NOT_IN_OBJECT_OR_COMPANION]">@JvmStatic fun a4()</error> {

    }
}
