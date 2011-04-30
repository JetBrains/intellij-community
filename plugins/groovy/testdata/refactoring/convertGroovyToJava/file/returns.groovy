class returns {
  def foo1() {
    print "foo"
  }

  void foo2() {
    print "foo"
  }

  int foo3() {
    print "foo"
  }

  def foo4() {1}
  def foo5(){return 2}
  int foo6(){2}
  def foo7() {
    if (true) 3
    else 4
  }
  int foo8() {
    if (true) 3
    else 4
  }
  void foo9() {
    if (true) 3
    else 4
  }
  void foo10() {
    if (true) return 3
    else 4
  }
  def foo11() {
    if (true)
      if (false) 4
      else 5
    else 4
  }
}