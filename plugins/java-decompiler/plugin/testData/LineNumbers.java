class LineNumbers {
  void m(double a, double b, double c) {
    double d = b*b - 4*a*c;
    if (d < 0) {
      System.out.println("No roots.");
    } else if (d == 0) {
      double x = -b / 2*a;
      System.out.println("x=" + x);
    } else {
      d = Math.sqrt(d);
      double x1 = -b - d / 2*a;
      double x2 = -b + d / 2*a;
      System.out.println("x1=" + x1 + " x2=" + x2);
    }
  }
}
