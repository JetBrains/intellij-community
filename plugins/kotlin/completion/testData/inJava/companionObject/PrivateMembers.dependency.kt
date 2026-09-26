package a

class Test {
    companion object {
        private fun secret() = Unit
        private val hiddenValue = 1
        fun visible() = Unit
        val publicValue = 2
    }
}
