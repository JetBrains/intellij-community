// "Fix all 'Unnecessary parentheses' problems in file" "true"
class Main {
  void foo(Object obj) {
      /*3*/
      /*b*/
      /*2*/
      /*a*/
      /*1*/
      /*c*/
      if (obj instanceof /*4*/ Point point/*d*/) {}
    if (obj instanceof Point point) {}
    if (obj instanceof Rect(Point(double x1, double x2), Point point2)) {}
    switch (obj) {
      case String str -> System.out.println(0);
      case Integer integer when integer == 1 -> System.out.println(1);
      default -> System.out.println(42);
    }
  }

  record Point(double x, double y) {}
  record Rect(Point point1, Point point2) {}
}
