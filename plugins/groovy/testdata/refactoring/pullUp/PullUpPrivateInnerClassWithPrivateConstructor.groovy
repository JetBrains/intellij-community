public class B extends A {
    private void f(){
        new C();
    }
      <caret>
    private static class C{
        private C(){

        }
    }
}

//A.java
class A {
}
