// LANGUAGE_VERSION: 1.8
import java.io.Serializable

object<caret> Foo : Serializable {
    private fun readResolve(): Any = Foo
}
