enum Planet {
  MERCURY(3.303e+23, 2.4397e6),
  VENUS(4.869e+24, 6.0518e6),
    protected static final CONST = 6.67300E-11
    public final double mass;
  public final double radius;

  Planet(double mass, double radius) {
    this.mass = mass;
    this.radius = radius;
  }

  double surfaceGravity() {
//      introduce a constant from the below numeric literal
    return CONST<caret> * mass / (radius * radius);
  }
}