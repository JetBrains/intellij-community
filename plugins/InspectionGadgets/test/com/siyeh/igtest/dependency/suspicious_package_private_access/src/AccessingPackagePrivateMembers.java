package xxx;

import static xxx.StaticMembers.*;

/**
 * @see PackagePrivateClass
 * @see PublicClass#packagePrivateField
 */
public class AccessingPackagePrivateMembers {
  Object field = new <warning descr="Class xxx.PackagePrivateClass is package-private, but declared in a different module 'dep'">PackagePrivateClass</warning>();
  {
    new <warning descr="Class xxx.PackagePrivateClass is package-private, but declared in a different module 'dep'">PackagePrivateClass</warning>();
  }
  static {
    new <warning descr="Class xxx.PackagePrivateClass is package-private, but declared in a different module 'dep'">PackagePrivateClass</warning>();
  }

  public void main() {
    new <warning descr="Class xxx.PackagePrivateClass is package-private, but declared in a different module 'dep'">PackagePrivateClass</warning>();
    <warning descr="Class xxx.PackagePrivateClass is package-private, but declared in a different module 'dep'">PackagePrivateClass</warning> variable;

    PublicClass aClass = new PublicClass();

    System.out.println(aClass.publicField);
    System.out.println(aClass.<warning descr="Field PublicClass.packagePrivateField is package-private, but declared in a different module 'dep'">packagePrivateField</warning>);
    System.out.println(PublicClass.PUBLIC_STATIC_FIELD);
    System.out.println(PublicClass.<warning descr="Field PublicClass.PACKAGE_PRIVATE_STATIC_FIELD is package-private, but declared in a different module 'dep'">PACKAGE_PRIVATE_STATIC_FIELD</warning>);

    aClass.publicMethod();
    aClass.<warning descr="Method PublicClass.packagePrivateMethod() is package-private, but declared in a different module 'dep'">packagePrivateMethod</warning>();
    Runnable r = aClass::<warning descr="Method PublicClass.packagePrivateMethod() is package-private, but declared in a different module 'dep'">packagePrivateMethod</warning>;

    System.out.println(<warning descr="Field StaticMembers.IMPORTED_FIELD is package-private, but declared in a different module 'dep'">IMPORTED_FIELD</warning>);
    <warning descr="Method StaticMembers.importedMethod() is package-private, but declared in a different module 'dep'">importedMethod</warning>();
  }
}