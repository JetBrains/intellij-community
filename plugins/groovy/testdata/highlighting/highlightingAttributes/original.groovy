@Annotation(parameter = 'value')
class C {
  C() {
    this(1, 2)
  }

  C(a, b) {
    super()
  }

  public instanceField
  def instanceProperty
  def getInstanceGetter() {}
  boolean isInstanceGetterBool() { false }
  void setInstanceSetter(p) {}

  public static staticField
  static def staticProperty
  static def getStaticGetter() {}
  static boolean isStaticGetterBool() { false }
  static void setStaticSetter(p) {}

  void instanceMethod(param) {
    def local = param
    label:
    for (a in local) {
      continue label
    }
  }

  static <T> void staticMethod(T reassignedParam) {
    T reassignedLocal = null
    reassignedParam = reassignedLocal
    reassignedLocal = reassignedParam
  }

  void 'method with literal name'() {}
}

abstract class AbstractClass {}
interface I {}
trait T {}
enum E {}
@interface Annotation {
  String parameter()
}

def c = new C() {}
c.instanceField

c.instanceProperty
c.instanceProperty = 42
c.instanceGetter
c.instanceGetterBool
c.instanceSetter = 42

c.getInstanceProperty()
c.setInstanceProperty(42)
c.getInstanceGetter()
c.isInstanceGetterBool()
c.setInstanceSetter(42)

c.instanceMethod()

C.staticField

C.staticProperty
C.staticProperty = 42
C.staticGetter
C.staticGetterBool
C.staticSetter = 42

C.getStaticProperty()
C.setStaticProperty(42)
C.getStaticGetter()
C.isStaticGetterBool()
C.setStaticSetter(42)

C.staticMethod()

c.'method with literal name'()

class Outer {

  def getThis() {}
  def getSuper() {}

  class Inner {
    def foo() {
      this
      super.hashCode()
      Outer.this
      Outer.super.toString()
    }
  }
}

def outer = new Outer()
outer.this
outer.super
