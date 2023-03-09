public class <warning descr="Class 'Quickfix' has only 'static' members, and a 'public' constructor"><caret>Quickfix</warning> {
  public Quickfix(){
  }
  public static void foo() {}
}

class <warning descr="Class 'Parent' has only 'static' members, and a 'public' constructor">Parent</warning> {
  public Parent(){
  }
  public static void foo() {}
}

class Child extends Parent{

}