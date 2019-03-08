import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

class Foo {
  void bar() {
    println 'Haha!'
  }
}

class Tor<D,U> {
  public void foo(@ClosureParams(value=FromString,options="List<U>") Closure cl) { cl.call([new Foo(), new Foo()]) }
}

@CompileStatic
def test() {
  def tor = new Tor<Integer,Foo>()

  tor.foo (it) -> { it.each (a) -> { a.bar() } }
}