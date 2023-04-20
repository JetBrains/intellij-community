class InnerStaticsJDK16
{
  class <warning descr="Inner class 'One' may be 'static'">One</warning> {
    class <warning descr="Inner class 'Two' may be 'static'">Two</warning> {}
  }
  public static void main(String[] args)
  {
    class Inner
    {
      class <warning descr="Inner class 'Nested' may be 'static'">Nested</warning> // can't be static
      {}
    }
    new Object() {
      class <warning descr="Inner class 'Y' may be 'static'">Y</warning> {}
    };

  }
}