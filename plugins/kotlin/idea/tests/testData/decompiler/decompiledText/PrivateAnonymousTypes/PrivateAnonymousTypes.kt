package test

interface Some

class PrivateAnonymousTypes<T> {
    private fun privateFn() = object : Some {}
    fun publicFn() = object : Some {}
}
