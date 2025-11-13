package testData.libraries

class JvmStatics {
    companion object {
        @JvmStatic
        fun `is`(key: String): Boolean = true

        @JvmStatic
        fun `is`(key: String, defaultValue: Boolean): Boolean = true
    }
}