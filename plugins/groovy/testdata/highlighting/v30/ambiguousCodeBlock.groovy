class Foo {
  def i
}

def method1() {
  return new Foo()
  {
  }
}

def method2() {
  foo new Foo()
  {
  }
}

def method3() {
  {print "foo"}.call()
}

void method4(Closure c) {
	c.call()
}

method4 {
	{println 'a' }
}