@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
public class Wrongsignature1 {
   @org.junit.runners.Parameterized.Parameters
    static Integer <warning descr="Method 'regExValues()' should be public and return Collection">regExValues</warning>() {
        return null;
    }
}