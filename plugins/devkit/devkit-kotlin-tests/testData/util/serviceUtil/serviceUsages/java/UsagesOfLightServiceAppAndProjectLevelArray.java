import serviceDeclarations.LightServiceAppAndProjectLevelArray;

class MyClazz3 {
  void foo3() {
    Object obj = <caret>LightServiceAppAndProjectLevelArray.getInstance();
  }
}