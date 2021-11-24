import java.util.concurrent.ThreadLocalRandom;

public interface Test {
  int foo();
  Test s1 = () -> 1;
  Test s2 = () -> ThreadLocalRandom.current().nextInt();
}