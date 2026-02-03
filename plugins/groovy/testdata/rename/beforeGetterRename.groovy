class Test {
  def foo = "abc"
}

Test test = new Test()
test.foo = "d"
test.fo<caret>o = "e"
test.foo = "f"