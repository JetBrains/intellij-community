// WITH_STDLIB
package jvmStatic

class C {
    companion object {
        @JvmStatic fun callStatic() {}
        fun callNonStatic() {}
    }
}