package com.example;

interface MyClosedService {
  void doSomething();
}

class MyClosedServiceImpl implements MyClosedService {
  @Override
  public void doSomething() {}
  public void implOnly() {}
}

class Consumer {
  // OK: non-open service can be cast to specific implementation
  void castNonOpenService(MyClosedService service) {
    ((MyClosedServiceImpl) service).implOnly();
  }
}
