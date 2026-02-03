package p1

import static p1.C1.E.e

class C1 {
  def foo() {
    print e
  }
  static enum E {
    e
  }
}