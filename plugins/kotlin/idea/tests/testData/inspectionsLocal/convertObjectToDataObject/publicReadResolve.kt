// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// PROBLEM: none
import java.io.Serializable

object<caret> Foo : Serializable {
    fun readResolve(): Any = Foo
}
