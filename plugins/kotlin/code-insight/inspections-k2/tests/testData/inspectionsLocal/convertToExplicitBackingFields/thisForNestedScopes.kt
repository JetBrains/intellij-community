// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
class Controller {
    private val _status = "OK"
    val status: CharSequence
        get() = _sta<caret>tus

    fun check() {
        val status = "LOCAL_VALUE"
        val r = object {
            fun log() = println(_status)
        }
    }
}