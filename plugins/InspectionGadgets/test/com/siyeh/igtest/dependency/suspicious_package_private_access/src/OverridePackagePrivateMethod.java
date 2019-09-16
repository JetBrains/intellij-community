package xxx;

class OverridePackagePrivateMethod extends PackagePrivateAbstractMethod {

  @Override
  public void <warning descr="Method foo() overrides a package-private method from class xxx.PackagePrivateAbstractMethod which is declared in a different module 'dep'">foo</warning>() {}

  @Override
  public void <warning descr="Method bar() overrides a package-private method from class xxx.PackagePrivateAbstractMethod which is declared in a different module 'dep'">bar</warning>() {}

  @Override
  public void baz() {}

  @Override
  public void qux() {}

}