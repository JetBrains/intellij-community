class OverridableMethodCallDuringObjectConstruction {
  {
    a();
    b();
    c();
    d();
  }

  void a() {}
  public void b() {}
  private void c() {}
  public final void d() {}
}
class One {
  public final void a() {}
}
class Two extends One {
  Two() {
    a();
  }
}
class A implements Cloneable {
  protected A clone() throws CloneNotSupportedException {
    return (A) super.clone();
  }
}