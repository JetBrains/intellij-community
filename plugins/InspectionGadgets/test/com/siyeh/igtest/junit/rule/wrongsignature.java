class Test {
  @org.junit.ClassRule public static org.junit.rules.TestRule f1;
  @org.junit.ClassRule public org.junit.rules.TestRule <warning descr="Fields annotated with @org.junit.ClassRule should be static">f2</warning>;
  @org.junit.ClassRule static org.junit.rules.TestRule <warning descr="Fields annotated with @org.junit.ClassRule should be public">f3</warning>;
  @org.junit.ClassRule org.junit.rules.TestRule <warning descr="Fields annotated with @org.junit.ClassRule should be public and static">f4</warning>;

  @org.junit.Rule public static org.junit.rules.TestRule <warning descr="Fields annotated with @org.junit.Rule should be non-static">f5</warning>;
  @org.junit.Rule public org.junit.rules.TestRule f6;
  @org.junit.Rule static org.junit.rules.TestRule <warning descr="Fields annotated with @org.junit.Rule should be public and non-static">f7</warning>;
  @org.junit.Rule org.junit.rules.TestRule <warning descr="Fields annotated with @org.junit.Rule should be public">f8</warning>;
  @org.junit.Rule public String <warning descr="Field type should be subtype of org.junit.rules.TestRule">f9</warning>;
  @org.junit.Rule public org.junit.rules.MethodRule f10;
}