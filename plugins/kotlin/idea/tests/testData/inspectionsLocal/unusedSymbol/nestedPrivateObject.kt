// PROBLEM: none

import C.CC.value

class C {
    fun value() = value

    // NOTE: CC is not used other than `import CC.value`, but `value` is used. We cannot delete `import CC.value`, so we have to keep `CC`.
    private object CC<caret> {
        const val value = 3
    }
}