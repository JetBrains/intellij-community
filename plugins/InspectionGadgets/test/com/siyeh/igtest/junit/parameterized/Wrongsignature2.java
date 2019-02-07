@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
public class Wrongsignature2 {
   @org.junit.runners.Parameterized.Parameters
   public static Integer <warning descr="Method 'regExValues()' should return Collection">regExValues</warning>() {
        return null;
    }
}