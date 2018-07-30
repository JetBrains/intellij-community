import java.util.*;

class Test {
  void simple(Object obj, Object obj2, String[] arr1, String[] arr2) {
    if(Objects.equals(obj, "")) System.out.println();
    if(Objects.<warning descr="Can be replaced with 'equals()'">equals</warning>("", obj)) System.out.println();
    if(Objects.equals(obj, obj2)) System.out.println();
    if(obj != null && Objects.<warning descr="Can be replaced with 'equals()'">equals</warning>(obj, obj2)) System.out.println();
    // Adding boxing would make things more complex, do not suggest anything here
    if(Objects.equals(1, obj)) System.out.println();
    // Comparing arrays this way is suspicious anyways: either arr1 == arr2 or Arrays.equals(arr1, arr2)
    // should be used; other inspections cover this, so no warning here
    if(arr1 != null && Objects.equals(arr1, arr2)) System.out.println();
    
  }
  
  void testOverridden(First f1, First f2, Second s1, Second s2, Third t1, Third t2) {
    // No conflicting equals signatures
    if(f1 != null && Objects.<warning descr="Can be replaced with 'equals()'">equals</warning>(f1, f2)) System.out.println();
    // Has instance equals method with single parameter: dangerous, can be bound to wrong method if quick-fix is applied
    if(s1 != null && Objects.equals(s1, s2)) System.out.println();
    if(t1 != null && Objects.equals(t1, t2)) System.out.println();

  }
  
  static class First {
    boolean equals(First f1, First f2) {
      return false;
    }

    static void equals(String s) {}
  }

  static class Second extends First {
    boolean equals(Second f) {
      return f == this;
    }
  }

  static class Third extends Second {
    
  }
}