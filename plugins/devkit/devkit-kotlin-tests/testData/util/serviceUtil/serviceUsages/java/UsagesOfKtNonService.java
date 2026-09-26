import serviceDeclarations.KtNonService;

class MyClazz2 {
  void foo2() {
    Object obj = <caret>KtNonService.getInstance();
  }
}