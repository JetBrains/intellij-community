class NumberEquality {

  boolean f(Integer i, Integer j) {
    return i <warning descr="Number objects are compared using '==', not 'equals()'">==</warning> j;
  }

  boolean g(Integer i) {
    return i == null;
  }
}