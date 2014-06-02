import java.util.Objects;

class Negated {

  boolean two(Object o1, Object o2) {
    return !Objects.equals(o1, o2);
  }
}