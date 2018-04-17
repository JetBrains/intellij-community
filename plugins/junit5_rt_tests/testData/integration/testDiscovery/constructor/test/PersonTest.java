public class PersonTest {
  @org.junit.Test
  public void testPerson() {
    org.junit.Assert.assertEquals(new Person("Foo"), new Person("Bar"));
  }
}