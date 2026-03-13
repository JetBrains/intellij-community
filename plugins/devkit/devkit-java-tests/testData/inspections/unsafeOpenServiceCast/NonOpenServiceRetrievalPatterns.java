package com.example;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

interface MyClosedService {
  static MyClosedService getInstance() {
    return null;
  }

  static MyClosedService getInstance(Project project) {
    return null;
  }

  void doSomething();
}

class MyClosedServiceImpl implements MyClosedService {
  @Override
  public void doSomething() {}
  public void implOnly() {}
}

class Consumer {
  // Cast of getInstance() result - OK for non-open service
  void castGetInstance() {
    MyClosedServiceImpl impl = (MyClosedServiceImpl) MyClosedService.getInstance();
  }

  // Cast of getInstance(param) result - OK for non-open service
  void castGetInstanceWithParam(Project project) {
    MyClosedServiceImpl impl = (MyClosedServiceImpl) MyClosedService.getInstance(project);
  }

  // Cast in method call chain - OK for non-open service
  void castInMethodChain() {
    ((MyClosedServiceImpl) MyClosedService.getInstance()).implOnly();
  }

  // Cast from getService() - OK for non-open service
  void castFromGetService(Project project) {
    MyClosedServiceImpl impl = (MyClosedServiceImpl) project.getService(MyClosedService.class);
  }

  // Cast from Application.getService() - OK for non-open service
  void castFromApplicationGetService() {
    MyClosedServiceImpl impl = (MyClosedServiceImpl) ApplicationManager.getApplication().getService(MyClosedService.class);
  }

  // Cast from parameter - OK for non-open service
  void castFromParameter(MyClosedService service) {
    MyClosedServiceImpl impl = (MyClosedServiceImpl) service;
  }

  // Cast to same type - OK
  void castToSameType() {
    MyClosedService service = (MyClosedService) MyClosedService.getInstance();
  }
}
