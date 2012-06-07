import java.lang.Comparable;

class Foo implements Comparable<Foo> {
  public int compareTo(Foo o) {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public int compareTo(String o) {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }
}