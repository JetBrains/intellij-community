class Field {
  Integer <caret>arr1 [] [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
  Integer <caret>arr2 @Required   []  [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
  Integer <caret>arr3 @Required   [] @Preliminary   @Required  [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
  Integer  @Required []  <caret>arr4 @Preliminary @Required [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
  Integer  @Required []  <caret>arr5 [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
  Integer [] <caret>arr6 @Preliminary   [];
}