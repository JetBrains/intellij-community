// PLATFORM: Common
// FILE: A.kt
expect open class BaseClass<T> {
    open fun T.base()
}
open class CommonChild1 : BaseClass<String>() {
    override fun String.base() {}
}

// PLATFORM: Jvm
// FILE: A.kt
// MAIN
open class JvmOnlyChild<K> : BaseClass<Int>() {
    open fun K.foo() {}
    override fun Int.base() {}
}

class JvmOnlySecondChild: JvmOnlyChild<String> {
    override fun String.foo() {}
}

actual open class BaseClass<T> actual constructor() {
    actual open fun T.base() {}
}

// PLATFORM: Js
// FILE: A.kt
actual open class BaseClass<T> actual constructor() {
    actual open fun T.base() {}
}
open class JsOnlyChild : BaseClass<String>() {
    override fun String.base() {}
}
