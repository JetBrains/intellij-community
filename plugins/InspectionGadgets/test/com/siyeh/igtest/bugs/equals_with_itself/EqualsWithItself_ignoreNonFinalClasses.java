import java.util.*;

class EqualsWithItself_ignoreNonFinalClasses {

  boolean foo(Object o) {
    return o.equals(o);
  }

  boolean string(String s) {
    return s.<warning descr="'equalsIgnoreCase()' called on itself">equalsIgnoreCase</warning>(s);
  }

  boolean integer(Integer i) {
    return i.<warning descr="'equals()' called on itself">equals</warning>(i);
  }

  boolean customClass(FinalClass finalClass) {
    return finalClass.equals(finalClass);
  }

  private static final class FinalClass{
  }
}