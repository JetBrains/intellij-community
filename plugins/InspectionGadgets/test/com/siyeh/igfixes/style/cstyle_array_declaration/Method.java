public class Method {

  public String/*2*/ <caret>test1(String[] a)/*1*/[] {
    return a;
  }

  public @Required Integer @Required @Preliminary[] <caret>test2() @Required   @Preliminary[] {
    return null;
  }

  public @Required Integer <caret>test3() @Required @Preliminary[] @Preliminary[]   {
    return null;
  }

  public Integer @Required   @Preliminary[] <caret>test4() [] {
    return null;
  }

  public Integer [] <caret>test5() @Required []  []  {
    retun null;
  }
}