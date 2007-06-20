class A {
  def foo
  A(Map m) {
    println "in constructor"
  }
}

def a = new A(<ref>foo : "bar")

println a.foo