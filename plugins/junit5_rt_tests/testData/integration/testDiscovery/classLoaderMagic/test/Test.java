public class Test {
  @org.junit.Test
  public void testClassLoaderMagic() throws Exception {
    ClassLoader cl = ClassLoader.getSystemClassLoader().getParent();
    org.junit.Assert.assertTrue(cl.getClass().getName().contains("ExtClassLoader"));
    org.junit.Assert.assertTrue(Class.forName("com.intellij.rt.coverage.data.TestDiscoveryProjectData").getClassLoader() == null);
    MyClassLoader.doMagic(cl);
  }
}