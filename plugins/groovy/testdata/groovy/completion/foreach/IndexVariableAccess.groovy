class Outer {
  def foo() {
    for(int idx, int value in [1, 2, 3]) {
      id<caret>
    }
  }
}