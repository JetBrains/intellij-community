enum class EnumFromCommon {
    I
}

fun getEnumEntryFromCommon(): EnumFromCommon = EnumFromCommon.I

fun getEnumTypeFromCommon(): Enum<*> = EnumFromCommon.I

fun <T : Enum<T>> getEnumTypeFromCommonGeneric(): Enum<T> = null!!
