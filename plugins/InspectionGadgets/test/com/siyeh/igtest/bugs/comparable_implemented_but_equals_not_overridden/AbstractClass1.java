abstract class <warning descr="Class 'AbstractClass1' implements 'java.lang.Comparable' but does not override 'equals()'">AbstractClass1</warning> implements Comparable<AbstractClass1> {

  int field = 1;

  public int compareTo(AbstractClass1 other) {
    return field > other.field ? 1 : (field == other.field ? 0 : -1);
  }
}