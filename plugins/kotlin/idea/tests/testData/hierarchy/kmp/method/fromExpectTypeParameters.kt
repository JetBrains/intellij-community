// PLATFORM: Common
// FILE: A.kt
// MAIN
expect open class BaseClass<T> {
    open fun T.base()
}
open class CommonChild1 : BaseClass<String>() {
    override fun String.base() {}
}

// PLATFORM: Jvm
// FILE: A.kt
actual open class BaseClass<T> actual constructor() {
    actual open fun T.base() {}
}
open class JvmOnlyChild : BaseClass<Int>() {
    override fun Int.base() {}
}

// PLATFORM: Js
// FILE: A.kt
actual open class BaseClass<T> actual constructor() {
    actual open fun T.base() {}
}
open class JsOnlyChild : BaseClass<String>() {
    override fun String.base() {}
}
