import java.util.Comparator;

class ComparatorMethodParameterNotUsed implements Comparator<String> {

  Comparator<Integer> c = (<warning descr="'compare()' parameter 'a' is not used">a</warning>, <warning descr="'compare()' parameter 'b' is not used">b</warning>) -> 0;
  Comparator<Integer> d = (a, b) -> { throw null; };

  public int compare(String <warning descr="'compare()' parameter 's1' is not used">s1</warning>, String <warning descr="'compare()' parameter 's2' is not used">s2</warning>) {
    return 0;
  }
}
class Comparator2 implements Comparator<Comparator2> {

  public int compare(Comparator2 c1, Comparator2 c2) {
    throw new UnsupportedOperationException();
  }
}
class IncompleteComparator implements Comparator<Boolean> {

  public int compare(Boolean b1, Boolean b2)<EOLError descr="'{' or ';' expected"></EOLError>
}