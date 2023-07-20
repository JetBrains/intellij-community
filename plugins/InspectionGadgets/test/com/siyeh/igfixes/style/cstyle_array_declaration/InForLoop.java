class InForLoop {
  void test(int i) {
  if (true) for (int ii[] = {0}, is<caret>[] = {}; i < 10; i++) {}

  for (Integer arr1 [] [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}}; i < array.length; i++) {
  }
  for (Integer arr2 @Required   []  [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}}; i < array.length; i++) {
  }
  for (Integer arr3 @Required   [] @Preliminary   @Required  [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}}; i < array.length; i++) {
  }
  for (Integer  @Required []  arr4 @Preliminary @Required [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}}; i < array.length; i++) {
  }
  for (Integer  @Required []  arr5 [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}}; i < array.length; i++) {
  }
}}