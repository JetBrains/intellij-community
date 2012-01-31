
class <warning descr="Class UnusedClass is unused">UnusedClass</warning> {}
class Bar {
  int <warning descr="Property unusedProperty is unused">unusedProperty</warning> = 2
  int usedProperty = 39
  int usedProperty2 = 39
  int usedProperty3 = 39
  def <warning descr="Method unusedMethod is unused">unusedMethod</warning>() {}
  Bar usedMethod() { this }

  Bar getUsedPropertyGetter() {}

  public static void main(String[] args) { usedPrivately() }

  private static void usedPrivately() {}
  private void <warning descr="Method unusedPrivately is unused">unusedPrivately</warning>() {}

}
println new Bar().usedMethod().usedProperty
new Bar().setUsedProperty2 42
println new Bar().getUsedProperty3()
println new Bar().usedPropertyGetter
