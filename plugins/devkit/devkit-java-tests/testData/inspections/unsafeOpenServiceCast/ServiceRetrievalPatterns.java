package com.example;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

interface MyService {
  static MyService getInstance() {
    return null;
  }

  static MyService getInstance(Project project) {
    return null;
  }

  void doSomething();
}

class MyServiceImpl implements MyService {
  @Override
  public void doSomething() {}
  public void implOnly() {}
}

class Consumer {
  // Cast of getInstance() result - should warn
  void castGetInstance() {
    MyServiceImpl impl = <warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">(MyServiceImpl) MyService.getInstance()</warning>;
  }

  // Cast of getInstance(param) result - should warn
  void castGetInstanceWithParam(Project project) {
    MyServiceImpl impl = <warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">(MyServiceImpl) MyService.getInstance(project)</warning>;
  }

  // Cast in method call chain - should warn
  void castInMethodChain() {
    (<warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">(MyServiceImpl) MyService.getInstance()</warning>).implOnly();
  }

  // Cast from getService() - should warn
  void castFromGetService(Project project) {
    MyServiceImpl impl = <warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">(MyServiceImpl) project.getService(MyService.class)</warning>;
  }

  // Cast from Application.getService() - should warn
  void castFromApplicationGetService() {
    MyServiceImpl impl = <warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">(MyServiceImpl) ApplicationManager.getApplication().getService(MyService.class)</warning>;
  }

  // Cast to same type - OK
  void castToSameType() {
    MyService service = (MyService) MyService.getInstance();
  }
}
