public class PersonTest {
  @org.junit.Test
  public void testPersonName1() {
    org.junit.Assert.assertEquals("Andromeda", new Person("Andromeda").getName());
  }
  @org.junit.Test
  public void testPersonName2() {
    org.junit.Assert.assertEquals("Andromeda", new Person("Andromeda").getName());
  }
}