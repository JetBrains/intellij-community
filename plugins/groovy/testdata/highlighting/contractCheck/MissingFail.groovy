import org.jetbrains.annotations.Contract

class Foo {

  @Contract("true->fail")
  void <warning descr="Contract clause 'true -> fail' is violated: no exception is thrown">assertFalse</warning>(boolean fail) {
  }

}
