class ANotException
class BException: Exception()
class CNotException

fun test() {
    try {

    } catch (e: <caret>) {

    }
}

// WITH_ORDER
// EXIST: BException
// EXIST: ANotException
// EXIST: CNotException

// IGNORE_K1