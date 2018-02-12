class useisEmptyMethod {

  boolean x(String s) {
    return s.<warning descr="Single character 'startsWith()' could be replaced with 'charAt()' expression"><caret>startsWith</warning>("x");
  }
}