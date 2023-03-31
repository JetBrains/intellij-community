import serviceDeclarations.RegisteredApplicationService;

class MyClass {
  // explicit constructor call
  static final RegisteredApplicationService myAppService1 = new RegisteredApplicationService();

  // constructor call in a chain
  static final <warning descr="Application service must not be assigned to a static final field">RegisteredApplicationService</warning> myAppService2 = new RegisteredApplicationService().getInstance();

}