class X {
  def foo(String s, int p = 2) {
    print s;
  }

  def foo(String s) {
    return foo(s, 2);
  }

  def main() {
    foo("a")
  }
}