// LANGUAGE_VERSION: 1.8
// PROBLEM: none
import java.io.Serializable

object<caret> Foo : Serializable {
    fun readResolve(): Any = Foo
}
