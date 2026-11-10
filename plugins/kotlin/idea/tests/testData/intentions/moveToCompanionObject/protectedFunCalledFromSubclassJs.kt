// JS

open class Base {
    protected open fun ho<caret>ok() = 3
}

class Sub : Base() {
    fun callHook() = hook()
}
