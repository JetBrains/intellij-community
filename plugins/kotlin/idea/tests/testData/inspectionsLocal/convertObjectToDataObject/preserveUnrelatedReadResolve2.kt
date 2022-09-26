// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
import java.io.Serializable

object<caret> Foo : Serializable {
    fun readResolve(): Foo = Foo
}
