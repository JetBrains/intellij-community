import serviceDeclarations.NonService

class MyClass {

  companion object {
    // not a service
    val myNonService1 = NonService.getInstance()

    // not a service
    val a = 5
  }

}
