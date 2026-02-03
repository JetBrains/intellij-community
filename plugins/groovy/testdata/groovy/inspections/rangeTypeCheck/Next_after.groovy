class Foo implements Comparable<Foo> {
  @Override
  int compareTo(Foo o) {
    return 0
  }

  def previous() {
    return this
  }

    def Foo next() {
        return null
    }
}

print new Foo().<caret>.new Foo()