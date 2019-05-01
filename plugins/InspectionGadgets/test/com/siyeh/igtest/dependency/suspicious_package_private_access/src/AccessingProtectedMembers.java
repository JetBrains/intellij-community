package xxx;

class AccessingProtectedMembersNotFromSubclass {
  void foo() {
    ProtectedMembers aClass = new ProtectedMembers();
    aClass.<warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>();
    ProtectedMembers.<warning descr="Method ProtectedMembers.staticMethod() is protected and used not through a subclass here, but declared in a different module 'dep'">staticMethod</warning>();
    new <warning descr="Constructor ProtectedConstructors.ProtectedConstructors() is protected and used not through a subclass here, but declared in a different module 'dep'">ProtectedConstructors</warning>();
    new <warning descr="Constructor ProtectedConstructors.ProtectedConstructors(int) is protected and used not through a subclass here, but declared in a different module 'dep'">ProtectedConstructors</warning>(1);
    new ProtectedConstructors() {};
    new ProtectedConstructors(1) {};
  }

  void baz() {
    class LocalSubclass extends ProtectedMembers {
      void bar() {
        method();
        staticMethod();
      }
    }
  }
}

class AccessingProtectedMembersFromSubclass extends ProtectedMembers {
  void foo() {
    method();
    staticMethod();
    ProtectedMembers.staticMethod();

    ProtectedMembers aClass = new ProtectedMembers();
    aClass.<warning descr="Method ProtectedMembers.method() is protected and used not through a subclass here, but declared in a different module 'dep'">method</warning>();
    AccessingProtectedMembersFromSubclass myInstance = new AccessingProtectedMembersFromSubclass();
    myInstance.method();

    ProtectedMembers.StaticInner inner1;
    StaticInner inner2;

    new Runnable() {
      public void run() {
        method();
        staticMethod();
      }
    };

    class LocalClass {
      void baz() {
        method();
        staticMethod();
      }
    }
  }

  public static class StaticInnerImpl1 extends ProtectedMembers.StaticInner {
  }

  public static class StaticInnerImpl2 extends StaticInner {
  }

  public class OwnInner {
    void bar() {
      method();
      staticMethod();
    }
  }

  public static class OwnStaticInner {
    void bar() {
      staticMethod();
    }
  }
}

class AccessingDefaultProtectedConstructorFromSubclass extends ProtectedConstructors {
}

class AccessingProtectedConstructorFromSubclass extends ProtectedConstructors {
  AccessingProtectedConstructorFromSubclass() {
    super(1);
  }
}