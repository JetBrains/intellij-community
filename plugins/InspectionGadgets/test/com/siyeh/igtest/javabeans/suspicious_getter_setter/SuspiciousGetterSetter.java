public class SuspiciousGetterSetter {

  private String myOne;
  private String myTwo;

  public String <warning descr="Getter 'getTwo()' returns field 'myOne'">getTwo</warning>() {
    return myOne;
  }

  public void <warning descr="Setter 'setTwo()' assigns field 'myOne'">setTwo</warning>(String two) {
    myOne = two;
  }
}