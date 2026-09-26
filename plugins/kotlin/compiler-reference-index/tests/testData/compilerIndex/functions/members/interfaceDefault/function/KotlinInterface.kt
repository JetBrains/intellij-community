package top.level

interface KotlinInterface {
    fun function<caret>() = Unit

    fun t() {
        function()
    }
}