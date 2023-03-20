// "Fix all 'Unnecessary parentheses' problems in file" "true"
class Main {
  void foo(Object obj) {
    if ((obj <caret>instanceof /*4*/(/*3*/(/*2*/(/*1*/Point point)/*a*/)/*b*/)/*c*/)/*d*/) {}
    if (obj instanceof ((Point point))) {}
    if (obj instanceof ((Rect(((Point(((double x1)), ((double x2))))), ((Point point2)))))) {}
    switch (obj) {
      case String str -> System.out.println(0);
      case ((Integer integer)) when ((((integer)) == 1)) -> System.out.println(1);
      default -> System.out.println(42);
    }
  }

  record Point(double x, double y) {}
  record Rect(Point point1, Point point2) {}
}
