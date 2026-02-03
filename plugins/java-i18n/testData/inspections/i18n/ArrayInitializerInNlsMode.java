import org.jetbrains.annotations.Nls;

class MyTest {
  private @Nls String[] array = new String[] {<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>};
  private @Nls String[] array1 = {<warning descr="Hardcoded string literal: \"foo1\"">"foo1"</warning>};
  private String[] array2 = new String[] {"foo2"};
  
  {
    array = new String [] {<warning descr="Hardcoded string literal: \"ifoo\"">"ifoo"</warning>};
    array[0] = <warning descr="Hardcoded string literal: \"iifoo\"">"iifoo"</warning>;
    array1 = new String [] {<warning descr="Hardcoded string literal: \"ifoo1\"">"ifoo1"</warning>};
    array2 = new String[] {"ifoo2"};
    array2[0] = "iifoo2";
  }
}