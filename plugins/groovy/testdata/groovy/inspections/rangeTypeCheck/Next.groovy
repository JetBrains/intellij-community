class Foo implements Comparable<Foo> {
  @Override
  int compareTo(Foo o) {
    return 0
  }

  def previous() {
    return this
  }

}

print new Foo().<caret>.new Foo()