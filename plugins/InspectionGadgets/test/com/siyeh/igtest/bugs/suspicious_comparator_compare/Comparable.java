class Test implements Comparable<Test> {
  @Override
  public int <warning descr="Comparator never returns negative values">compareTo</warning>(Test <warning descr="'compareTo()' parameter 'o' is not used">o</warning>) {
    return <warning descr="Comparator does not return 0 for equal elements">1</warning>;
  }
}
class RetZero implements Comparable<RetZero> {
  @Override
  public int compareTo(RetZero o) {
    return 0;
  }
}
class NoReflexivity implements Comparable<NoReflexivity> {
  @Override
  public int compareTo(NoReflexivity o) {
    return this == o ? <warning descr="Comparator does not return 0 for equal elements">1</warning> : -1;
  }
}