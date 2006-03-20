class Test {
  boolean foo(){
    return false;
  }

  void bar(){
    if (foo()){
      return;
    }
  }

  void bar1(){
    if (!foo()){
      return;
    }
  }
}