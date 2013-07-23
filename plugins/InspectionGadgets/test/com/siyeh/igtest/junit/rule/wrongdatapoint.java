class Test {
  @org.junit.experimental.theories.DataPoint public static Object f1;
  @org.junit.experimental.theories.DataPoint public Object <warning descr="Fields annotated with @DataPoint should be static">f2</warning>;
  @org.junit.experimental.theories.DataPoint static Object <warning descr="Fields annotated with @DataPoint should be public">f3</warning>;
  @org.junit.experimental.theories.DataPoint Object <warning descr="Fields annotated with @DataPoint should be public and static">f4</warning>;
  @org.junit.experimental.theories.DataPoint Object <warning descr="Methods annotated with @DataPoint should be public and static">f4</warning>(){return null;}
}
