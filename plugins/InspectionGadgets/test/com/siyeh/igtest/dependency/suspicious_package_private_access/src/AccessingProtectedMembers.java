package xxx;

class AccessingProtectedMembersNotFromSubclass {
  void foo() {
    ProtectedMembers aClass = new ProtectedMembers();
    aClass.<warning descr="Method 'ProtectedMembers.method' is protected and used not from a subclass here, but declared in a different module 'dep'">method</warning>();
    ProtectedMembers.<warning descr="Method 'ProtectedMembers.staticMethod' is protected and used not from a subclass here, but declared in a different module 'dep'">staticMethod</warning>();
  }
}

class AccessingProtectedMembersFromSubclass extends ProtectedMembers {
  void foo() {
    method();
    staticMethod();
    ProtectedMembers.staticMethod();

    ProtectedMembers aClass = new ProtectedMembers();
    aClass.<warning descr="Method 'ProtectedMembers.method' is protected and used not from a subclass here, but declared in a different module 'dep'">method</warning>();
    AccessingProtectedMembersFromSubclass myInstance = new AccessingProtectedMembersFromSubclass();
    myInstance.method();
  }
}