// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
class Service {
    /**
     * Some useful documentation
     */
    private val _apiKey = "secret_string"

    val apiKey: CharSequence
        get() = _apiKey<caret>
}