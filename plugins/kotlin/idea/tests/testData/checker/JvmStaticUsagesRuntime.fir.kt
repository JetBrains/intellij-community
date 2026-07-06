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
        val s = object {
            @JvmStatic fun a3() {

            }
        }
    }

    @JvmStatic fun a4() {

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
        val s = object {
            @JvmStatic fun a3() {

            }
        }
    }

    @JvmStatic fun a4() {

    }
}
