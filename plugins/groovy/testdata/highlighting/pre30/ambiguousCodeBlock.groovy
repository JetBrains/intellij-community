class Foo {
  def i
}

def method1() {
  return new Foo()
    <error descr="Ambiguous code block">{
    }</error>
}

def method2() {
  foo new Foo()
  <error descr="Ambiguous code block">{
  }</error>
}

def method3() {
  <error descr="Ambiguous code block">{print "foo"}</error>.call()
}

void method4(Closure c) {
  c.call()
}

method4 {
  <error descr="Ambiguous code block">{println 'a' }</error>
}