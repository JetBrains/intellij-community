open interface Bar : java.io.Serializable

open interface Baz : Bar

object Foo<caret> : Baz
