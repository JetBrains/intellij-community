import java.util.HashMap;
import java.util.Map;

class CastNeeded1 {

  void m() {
    HashMap<Long, Integer> map = new HashMap<>();
    for (Map.Entry<Long, Integer> entry : ((map.entrySet()))) {
      pass((int) (long) entry.getKey(), entry.getValue());
    }
  }
}