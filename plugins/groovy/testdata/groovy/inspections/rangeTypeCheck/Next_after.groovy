class Foo implements Comparable<Foo> {
  @Override
  int compareTo(Foo o) {
    return 0
  }

  def previous() {
    return this
  }

  def Foo next() {
    return null  //To change body of implemented methods use File | Settings | File Templates.
  }
}

print new Foo()..new Foo()