import static p1.Outer.*

class PublicInheritor extends PublicClass {}
class ProtectedInheritor extends <warning descr="Access to 'ProtectedClass' exceeds its access rights">ProtectedClass</warning> {}
class PackageLocalInheritor extends <warning descr="Access to 'PackageLocalClass' exceeds its access rights">PackageLocalClass</warning> {}
class PrivateClassInheritor extends <warning descr="Access to 'PrivateClass' exceeds its access rights">PrivateClass</warning> {}

def usage() {
  println PublicClass.class
  println <warning descr="Access to 'ProtectedClass' exceeds its access rights">ProtectedClass</warning>.class
  println <warning descr="Access to 'PackageLocalClass' exceeds its access rights">PackageLocalClass</warning>.class
  println <warning descr="Access to 'PrivateClass' exceeds its access rights">PrivateClass</warning>.class

  println new PublicClass()
  println new <warning descr="Access to 'ProtectedClass' exceeds its access rights">ProtectedClass</warning>()
  println new <warning descr="Access to 'PackageLocalClass' exceeds its access rights">PackageLocalClass</warning>()
  println new <warning descr="Access to 'PrivateClass' exceeds its access rights">PrivateClass</warning>()
}

class Inheritor extends p1.Outer {
  static class PublicInheritor extends PublicClass {}
  static class ProtectedInheritor extends ProtectedClass {}
  static class PackageLocalInheritor extends <warning descr="Access to 'PackageLocalClass' exceeds its access rights">PackageLocalClass</warning> {}
  static class PrivateClassInheritor extends <warning descr="Access to 'PrivateClass' exceeds its access rights">PrivateClass</warning> {}
}
