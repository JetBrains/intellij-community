import java.util.*;

class X11 {

  public boolean <warning descr="Method 'equals()' is identical to its super method">equals</warning>(Object obj) {
    return obj == this;
  }
}
class MyList2 extends ArrayList {
  @Override
  protected void <warning descr="Method 'removeRange()' only delegates to its super method">removeRange</warning>(int a, int b) {
    super.removeRange(a, b);
  }

  public boolean <warning descr="Method 'contains()' is identical to its super method">contains</warning>(Object o) {
    return indexOf(o) >= 0;
  }
}