class OverridableMethodCallDuringObjectConstruction {
  {
    a();
    <warning descr="Call to overridable method 'b()' during object construction">b</warning>();
    c();
    d();
  }

  void a() {}
  public void b() {}
  private void c() {}
  public final void d() {}
}
class One {
  public void a() {}
}
class Two extends One {
  Two() {
    <warning descr="Call to overridable method 'a()' during object construction"><caret>a</warning>();
  }
}
class A implements Cloneable {
  protected A clone() throws CloneNotSupportedException {
    return (A) super.clone();
  }
}
class InnerCall {

  protected long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  public class Item {
    final long now;

    public Item() {
      now = currentTimeMillis();
    }
  }
}