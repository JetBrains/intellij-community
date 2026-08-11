package toplevelObjectDeclarations
  open class Foo(y : Int) {
    open fun foo() : Int = 1
  }

  <error descr="[NO_VALUE_FOR_PARAMETER]">class T : <error descr="[SUPERTYPE_NOT_INITIALIZED]">Foo</error> {}</error>

  object A : <error descr="[SUPERTYPE_NOT_INITIALIZED]">Foo</error> {
    val x : Int = 2

    fun test() : Int {
      return x + foo(<error descr="[NO_VALUE_FOR_PARAMETER]">)</error>
    }
  }

  object B : <error descr="[SINGLETON_IN_SUPERTYPE]">A</error> {}

  val x = A.foo()

  val y = object : Foo(x) {
    init {
      x + 12
    }

    override fun foo() : Int = 1
  }

  val z = y.foo()
