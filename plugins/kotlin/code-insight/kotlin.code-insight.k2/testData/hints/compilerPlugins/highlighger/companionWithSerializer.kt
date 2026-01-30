import kotlinx.serialization.*

class A {
    companion object {
        fun serializer(): KSerializer<A>
    }
}