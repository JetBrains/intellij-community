class LocalVariable {
  public void test() {
    Integer[][] arr1 = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
    Integer @Required [][] arr2 = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
    Integer @Required [] @Preliminary @Required [] arr3 = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
    Integer @Required [] @Preliminary @Required [] arr4 = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
    Integer @Required [][] arr5 = new Integer[][]{{3, 3, 3}, {3, 3, 3}};
    Integer[] @Preliminary [] arr6;
  }
}