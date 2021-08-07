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
}
interface XYZ {}