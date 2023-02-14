class InstanceofInterfaces {

  private final String s = "";

  public boolean equals(Object object) {
    if (this == object) return true;
    if (!(object instanceof InstanceofInterfaces)) return false;
    if (!super.equals(object)) return false;

    final InstanceofInterfaces that = (InstanceofInterfaces)object;

    if (!s.equals(that.s)) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + s.hashCode();
    return result;
  }

  void catWalkingOverKeyboard(Object a, Object b) {
    if (a instanceof <warning descr="'instanceof' concrete class 'InstanceofInterfaces'">InstanceofInterfaces</warning>) {
      System.out.println("!");
    }
    if (b instanceof XYZ) {
      System.out.println("?");
    }
  }

  void testEquality(Object a) {
    if(a.getClass() == <warning descr="Class comparison against concrete class 'InstanceofInterfaces'">InstanceofInterfaces</warning>.class) {}
    if(a.getClass() != <warning descr="Class comparison against concrete class 'InstanceofInterfaces'">InstanceofInterfaces</warning>.class) {}
    if(<warning descr="Class comparison against concrete class 'InstanceofInterfaces'">InstanceofInterfaces</warning>.class != a.getClass()) {}
    if(a.getClass().equals(<warning descr="Class comparison against concrete class 'InstanceofInterfaces'">InstanceofInterfaces</warning>.class)) {}
    if(!<warning descr="Class comparison against concrete class 'InstanceofInterfaces'">InstanceofInterfaces</warning>.class.equals(a.getClass())) {}
    if(a.getClass() == XYZ.class) {}
  }

  void testRecord(XYZ xyz) {
    if (xyz instanceof X x) {}
    if (xyz instanceof <warning descr="Pattern test against a concrete class 'Y'">Y</warning> y) {}
    if (xyz instanceof (<warning descr="Pattern test against a concrete class 'Y'">Y</warning> y && y != null)) {}
  }

  void testSwitch(XYZ xyz) {
    switch (xyz) {
      case X x -> {}
      case <warning descr="Pattern test against a concrete class 'Y'">Y</warning> y -> {}
      default -> {}
    }
  }
}
interface XYZ {}
record X() implements XYZ {}
class Y implements XYZ {}