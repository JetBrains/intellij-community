class Parameter {
  public void test1(String <caret>arr[]) {
  }

  public void test2(@Required Integer @Required @Preliminary[] <caret>arr @Required @Preliminary[]) {
  }

  public void test3(@Required Integer <caret>arr    @Required @Preliminary [  ] @Preliminary[]) {
  }

  public void test4(Integer @Required   @Preliminary[] <caret>arr  []) {
  }

  public void test5(Integer [] <caret>arr @Required [] []) {
  }
}