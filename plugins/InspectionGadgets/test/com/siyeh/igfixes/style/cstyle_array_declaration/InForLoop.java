class InForLoop {
  void test(int i) {
  if (true) for (int ii[] = {0}, is<caret>[] = {}; i < 10; i++) {}

  for (Integer <caret>arr1 [] [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}}; i < array.length; i++) {
  }
  for (Integer <caret>arr2 @Required   []  [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}}; i < array.length; i++) {
  }
  for (Integer <caret>arr3 @Required   [] @Preliminary   @Required  [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}}; i < array.length; i++) {
  }
  for (Integer  @Required []  <caret>arr4 @Preliminary @Required [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}}; i < array.length; i++) {
  }
  for (Integer  @Required []  <caret>arr5 [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}}; i < array.length; i++) {
  }
}}