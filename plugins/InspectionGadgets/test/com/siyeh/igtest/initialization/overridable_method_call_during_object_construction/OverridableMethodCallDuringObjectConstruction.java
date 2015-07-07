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