public class Method {

  public String[]/*2*/ test1(String[] a)/*1*/ {
    return a;
  }

  public @Required Integer @Required @Preliminary [] @Required @Preliminary [] test2() {
    return null;
  }

  public @Required Integer @Required @Preliminary [] @Preliminary [] test3() {
    return null;
  }

  public Integer @Required @Preliminary [][] test4() {
    return null;
  }

  public Integer[] @Required [][] test5() {
    retun null;
  }
}