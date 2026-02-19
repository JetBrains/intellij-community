import java.io.Serializable

fun consumeSerializable(<!UNUSED_PARAMETER!>s<!>: Serializable) { }

fun directReference() {
    consumeSerializable(EnumFromCommon.I)
}

fun indirectReference() {
    consumeSerializable(getEnumEntryFromCommon())
}

fun enumType() {
    consumeSerializable(getEnumTypeFromCommon())
}

fun <T : Enum<T>> enumTypeGeneric() {
    consumeSerializable(getEnumTypeFromCommonGeneric<T>())
}
