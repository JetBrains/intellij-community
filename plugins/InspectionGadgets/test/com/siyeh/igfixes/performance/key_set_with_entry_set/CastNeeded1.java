import java.util.HashMap;

class CastNeeded1 {

  void m() {
    HashMap<Long, Integer> map = new HashMap<>();
    for (long l : ((<caret>map.keySet()))) {
      pass((int)l, (map).get((l)));
    }
  }
}