class MyTest {

  boolean x(String s) {
    return s.<warning descr="Single character 'startsWith()' could be replaced with 'charAt()' expression"><caret>startsWith</warning>/*c1*/("x")//c2 
           && 
           !s.<warning descr="Single character 'endsWith()' could be replaced with 'charAt()' expression">endsWith</warning>("x");
  }
}