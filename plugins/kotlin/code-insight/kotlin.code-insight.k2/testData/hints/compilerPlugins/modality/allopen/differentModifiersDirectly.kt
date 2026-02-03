@Open
/*<# open #>*/class NoModifiers

@Open
interface Interface

@Open
abstract class Abstract

@Open
object Object

@Open
/*<# open #>*/public class Public

@Open
final class Final

@Open
sealed class Sealed

@Open
annotation class Annotation

@Open
enum class Enum {
    X, Y, Z
}

@Open
@Deprecated("")
/*<# open #>*/class AnnotatedNewLine

@Open
@Deprecated("") /*<# open #>*/class AnnotatedSameLine

class Outer {
    @Open
    /*<# open #>*/public inner class Inner

    @Open
    companion object
}

@Open
inline class Inline(val value: Int)

@JvmInline
@Open
value class Value(val value: Int)

@Deprecated("")
@Open
internal abstract sealed class AlotOfModifiersImplicit