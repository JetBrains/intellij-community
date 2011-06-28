class Foo implements Comparable<Foo> {
  def next() {return this}
  def previous() {return this}

    @Override
    int compareTo(Foo o) {
        return 0  //To change body of implemented methods use File | Settings | File Templates.
    }
}

class X {
  def foo() {
    final ObjectRange range = new Foo().<caret>.new Foo()
  }
}
