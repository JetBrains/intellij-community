public class PersonTest2 {
  @org.junit.Test
  public void testPersonName() {
    org.junit.Assert.assertEquals("Andromeda", new Person("Andromeda").getName());
  }
}