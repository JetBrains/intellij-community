import serviceDeclarations.NonService;

class MyClazz1 {
  void foo1() {
    Object obj = <caret>NonService.getInstance();
  }
}