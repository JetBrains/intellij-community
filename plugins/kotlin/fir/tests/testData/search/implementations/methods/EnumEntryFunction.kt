enum class SampleEnum {
    V1 {
        override fun any() { super.any() }
    };

    open fun a<caret>ny() {}
}
