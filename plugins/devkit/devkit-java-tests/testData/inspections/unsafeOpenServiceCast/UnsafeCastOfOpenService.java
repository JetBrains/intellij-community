package com.example;

interface MyService {
  void doSomething();
}

class MyServiceImpl implements MyService {
  @Override
  public void doSomething() {}
  public void implOnly() {}
}

class Consumer {
  // Cast to subclass - should report
  void castToImpl(MyService service) {
    (<warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">(MyServiceImpl) service</warning>).implOnly();
  }

  // Cast to same type - OK
  void castToSameType(MyService service) {
    ((MyService) service).doSomething();
  }

  // From parameter
  void fromParameter(MyService s) {
    MyServiceImpl impl = <warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">(MyServiceImpl) s</warning>;
  }

  // From field
  private MyService field;
  void fromField() {
    MyServiceImpl impl = <warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">(MyServiceImpl) field</warning>;
  }
}
