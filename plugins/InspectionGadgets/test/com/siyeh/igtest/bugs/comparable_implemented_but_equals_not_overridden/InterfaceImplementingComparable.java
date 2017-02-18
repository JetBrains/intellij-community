interface ComparableInterface extends Comparable<ComparableInterface> {
  int get();
}

interface ComparableImpl extends ComparableInterface {
  @Override
  default int compareTo(ComparableInterface o) {
    return 0;
  }
}

abstract class Impl implements ComparableImpl {
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Impl impl = (Impl) o;
    return get() == impl.get();
  }

  @Override
  public int hashCode() {
    return get();
  }
}