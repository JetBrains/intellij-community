@Open
interface Super

/*<# open #>*/class NoModifiers : Super

interface Interface : Super

abstract class Abstract : Super

object Object : Super

/*<# open #>*/public class Public : Super

final class Final : Super

sealed class Sealed : Super

annotation class Annotation : Super

enum class Enum : Super {
    X, Y, Z
}

@Deprecated("")
/*<# open #>*/class AnnotatedNewLine : Super

@Deprecated("") /*<# open #>*/class AnnotatedSameLine : Super

class Outer {
    /*<# open #>*/public inner class Inner : Super

    companion object : Super

}

inline class Inline(val value: Int) : Super

@JvmInline
value class Value(val value: Int) : Super

@Deprecated("")
internal abstract sealed class AlotOfModifiersImplicit : Super