// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// PROBLEM: none
import java.io.Serializable

open class Base {
    protected fun readResolve(): Any = Foo
}

object<caret> Foo : Base(), Serializable {
    override fun toString(): String = "Foo"
}
