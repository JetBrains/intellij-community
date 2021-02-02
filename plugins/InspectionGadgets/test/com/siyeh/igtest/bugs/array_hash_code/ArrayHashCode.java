import java.util.Objects;

class ArrayHashCode {

  int one(String[] ss) {
    return ss.<warning descr="'hashCode()' called on array should probably be 'Arrays.hashCode()'">hashCode</warning>();
  }

  int two(String[][] ss) {
    return ss.<warning descr="'hashCode()' called on array should probably be 'Arrays.hashCode()'">hashCode</warning>();
  }

  int three(String s1, String[] ss2, String[][] ss3) {
    return Objects.hash(s1, <warning descr="Array passed to 'Objects.hash()' should be wrapped in 'Arrays.hashcode()'">ss2</warning>, <warning descr="Array passed to 'Objects.hash()' should be wrapped in 'Arrays.hashcode()'">ss3</warning>);
  }
}