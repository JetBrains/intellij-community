import org.example.BaseClass
import org.example.DerivedClass
import spock.lang.Specification

class Steps {
  def method() {}
}

class A {
  def x
  Steps s

  A(def x, Steps s) {
    this.x = x
    this.s = s
  }

  void m() {}
}

class B extends A {
  B(def x, Steps s) {
    super(x, s)
  }
}



class MyTest extends Specification {
  def classesWithNoFields() {
        given:
        BaseClass a = Spy(DerivedClass, name: "S", verified: true, constructorArgs: [])
        DerivedClass <warning descr="Cannot assign 'BaseClass' to 'DerivedClass'">b</warning> = Spy(BaseClass, constructorArgs: [])
        BaseClass c = Mock(DerivedClass, name: "S", verified: true, constructorArgs: [])
        DerivedClass <warning descr="Cannot assign 'BaseClass' to 'DerivedClass'">d</warning> = Mock(BaseClass, name: "S", verified: true, constructorArgs: [])
        BaseClass e = Stub(DerivedClass, name: "S", verified: true, constructorArgs: [])
        DerivedClass <warning descr="Cannot assign 'BaseClass' to 'DerivedClass'">f</warning> = Stub(BaseClass, constructorArgs: [])
        BaseClass g = GroovySpy(DerivedClass, name: "S", verified: true, constructorArgs: [])
        DerivedClass <warning descr="Cannot assign 'BaseClass' to 'DerivedClass'">h</warning> = GroovySpy(BaseClass, constructorArgs: [])
        BaseClass i = GroovyMock(DerivedClass, name: "S", verified: true, constructorArgs: [])
        DerivedClass <warning descr="Cannot assign 'BaseClass' to 'DerivedClass'">j</warning> = GroovyMock(BaseClass, name: "S", verified: true, constructorArgs: [])
        BaseClass k = GroovyStub(DerivedClass, name: "S", verified: true, constructorArgs: [])
        DerivedClass <warning descr="Cannot assign 'BaseClass' to 'DerivedClass'">l</warning> = GroovyStub(BaseClass, constructorArgs: [])
  }

  def classesWithFields() {
        given:
        def x = 'str'
        Steps s = Mock()
        A a = Spy(B, constructorArgs: [x, s])
        B <warning descr="Cannot assign 'A' to 'B'">b</warning> = Spy(A, constructorArgs: [x, s])
        A c = Mock(B, constructorArgs: [x, s])
        B <warning descr="Cannot assign 'A' to 'B'">d</warning> = Mock(A, constructorArgs: [x, s])
        A e = Stub(B, constructorArgs: [x, s])
        B <warning descr="Cannot assign 'A' to 'B'">f</warning> = Stub(A, constructorArgs: [x, s])
        A g = GroovySpy(B, constructorArgs: [x, s])
        B <warning descr="Cannot assign 'A' to 'B'">h</warning> = GroovySpy(A, constructorArgs: [x, s])
        A i = GroovyMock(B, constructorArgs: [x, s])
        B <warning descr="Cannot assign 'A' to 'B'">j</warning> = GroovyMock(A, constructorArgs: [x, s])
        A k = GroovyStub(B, constructorArgs: [x, s])
        B <warning descr="Cannot assign 'A' to 'B'">l</warning> = GroovyStub(A, constructorArgs: [x, s])
  }
}