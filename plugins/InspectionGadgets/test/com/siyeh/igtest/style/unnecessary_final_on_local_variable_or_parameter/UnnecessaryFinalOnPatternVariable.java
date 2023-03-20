class Foo {
  record Point(int x, int y) {}

  record Rect(Point point1, Point point2) {}

  void test1(Object obj) {
    if (obj instanceof Rect(Point(<warning descr="Unnecessary 'final' on parameter 'x1'">final</warning> int x1, <warning descr="Unnecessary 'final' on parameter 'y1'">final</warning> int y1), <warning descr="Unnecessary 'final' on parameter 'point2'">final</warning> Point point2)) {}
  }

  void test2(Object obj) {
    switch (obj) {
      case Point(<warning descr="Unnecessary 'final' on parameter 'x'">final</warning> int x, <warning descr="Unnecessary 'final' on parameter 'y'">final</warning> int y) when x == y -> {}
      case <warning descr="Unnecessary 'final' on parameter 'rect'">final</warning> Rect rect -> {}
      default -> {}
    }
  }

  void test3(Rect[] rects) {
    for (Rect(Point(<warning descr="Unnecessary 'final' on parameter 'x1'">final</warning> int x1, <warning descr="Unnecessary 'final' on parameter 'y1'">final</warning> int y1), <warning descr="Unnecessary 'final' on parameter 'point2'">final</warning> Point point2) : rects) {

    }
  }
}
