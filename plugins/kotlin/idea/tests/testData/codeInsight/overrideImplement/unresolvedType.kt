// FIR_IDENTICAL
// DISABLE_ERRORS
interface A<in TPipeline, out TBuilder : Any, TFeature : Any> {
    fun install(pipeline: TPipeline, configure: TBuilder.() -> Unit): TFeature
}

class X : A<String, UnresolvedType, Unit> {
    <caret>
}

// MEMBER: "install(pipeline: String, configure: UnresolvedType.() -> Unit): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"