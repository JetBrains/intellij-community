class Test {
  public static boolean test(String key, String keyValue){
    boolean b;
    <caret>if(key != null && key.equals(keyValue)) {
              // some comment goes here
              b = true;
          } else {
              b = false;
          }
      }
}