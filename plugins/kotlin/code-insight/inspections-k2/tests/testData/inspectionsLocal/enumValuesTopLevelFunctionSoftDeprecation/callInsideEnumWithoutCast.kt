enum class EnumClass {
    ;
    init {
        for (e in enumValues<caret><EnumClass>()) {}
    }
}

