class Test {
  public static boolean test(String key, String keyValue){
    boolean b;
      <caret>// some comment goes here
      /*3*/
      /*4*/
      b/*1*/ = /*2*/key == null || /*5*/!key.equals(keyValue);
      }
}