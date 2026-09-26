// FIR_IDENTICAL
// DISABLE_ERRORS
interface Serializer {
    fun getSerializer(kind: String): KSerializer<out XDescriptor>?
}

class MyImpl : Serializer {
    <caret>
}

// MEMBER: "getSerializer(kind: String): KSerializer<out XDescriptor>?"
