abstract class A implements Comparable<A> {
  {
    new <warning descr="Class 'A' implements 'java.lang.Comparable' but does not override 'equals()'"><caret>A</warning>() {
      public int compareTo(A a){
        return 0;
      }
    };
  }
}