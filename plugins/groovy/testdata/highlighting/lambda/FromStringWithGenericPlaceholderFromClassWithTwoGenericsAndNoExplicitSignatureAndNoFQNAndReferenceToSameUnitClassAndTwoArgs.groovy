import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

class Foo {
  void bar() {
    println 'Haha!'
  }
}

class Tor<D,U> {
  public void foo(@ClosureParams(value=FromString,options=["D,List<U>"]) Closure cl) { cl.call(3, [new Foo(), new Foo()]) }
}

@CompileStatic
def test() {

  def tor = new Tor<Integer,Foo>()

  tor.foo (r, e) -> { r.times (t) -> { e.each (it) -> { it.bar() } } }
}