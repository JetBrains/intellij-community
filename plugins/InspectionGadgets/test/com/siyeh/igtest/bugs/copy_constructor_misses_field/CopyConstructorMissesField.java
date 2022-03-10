import java.util.HashMap;
import java.util.Map;

class CopyConstructorMissesField {

  private String name;

  CopyConstructorMissesField(CopyConstructorMissesField other) {
    name = other.name;
  }
}
class Child extends CopyConstructorMissesField {
  private String field1;
  private String field2;

  <warning descr="Copy constructor does not copy field 'field2'">Child</warning>(Child other) {
    super(other);
    field1 = other.field1;
  }
}
class Line {
  Point p1, p2;

  Line(int x1, int y1, int x2, int y2) {
    p1 = new Point(x1, y1);
    p2 = new Point(x2, y2);
  }

  Line(Line other) { // Copy constructor does not copy fields 'p1' and 'p2'
    this(other.p1.x, other.p1.y, other.p2.x, other.p2.y);
  }
}
class Point {
  final int x, y;

  Point(int x, int y) {
    this.x = x;
    this.y = y;
  }
}
class B {
  int[] data;

  public B(int[] data) {
    this.data = data;
  }

  // IDEA warns that 'data' field is not copied
  B(B b) {
    this(java.util.Arrays.copyOf(b.data, b.data.length));
  }
}
class WithGetters {
  private Map<String, String> map;

  public Map<String, String> getMap() {
    if (map == null)
      return new HashMap<>();
    else return map;
  }

  public WithGetters(Map<String, String> map) {
    this.map = map;
  }
  // warning on constructor below, that it does not copy 'map'
  public WithGetters(WithGetters withGetters) {
    this(withGetters.getMap());
  }
}
class FinalField {
  final int id;

  public FinalField() {
    id = new java.util.Random().nextInt();
  }

  // warning on constructor below
  public FinalField(FinalField ff) {
    this();
    //copy other fields
  }
}