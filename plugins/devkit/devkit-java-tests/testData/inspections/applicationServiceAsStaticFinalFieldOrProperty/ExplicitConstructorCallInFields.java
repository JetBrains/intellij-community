import serviceDeclarations.RegisteredApplicationService;

class MyClass {
  // explicit constructor call
  static final RegisteredApplicationService myAppService1 = new RegisteredApplicationService();

  // constructor call in a chain
  static final RegisteredApplicationService <warning descr="Application service must not be assigned to a static final field">myAppService2</warning> = new RegisteredApplicationService().getInstance();

}