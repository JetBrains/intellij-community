import serviceDeclarations.RegisteredApplicationService;

class MyClass {
  // explicit constructor call
  static final RegisteredApplicationService myAppService1 = new RegisteredApplicationService();

  <warning descr="Application service must not be assigned to a static final field">// constructor call in a chain
  static final RegisteredApplicationService myAppService2 = new RegisteredApplicationService().getInstance();</warning>

}