import org.jetbrains.annotations.NotNull

class Foo implements Comparable<Foo> {
  def next() {return this}
  def previous() {return this}

    @Override
    int compareTo(@NotNull Foo o) {
        <selection>return 0</selection>
    }
}

class X {
  def foo() {
    final ObjectRange range = new Foo()..new Foo()
  }
}
