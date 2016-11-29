class Outer {
  class Inner {
    private Outer outer() {
      return Outer.this;
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof Inner) &&
              Outer.this <caret>== ((Inner) obj).outer();
    }
  }
}