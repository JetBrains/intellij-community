import java.util.List;
import java.util.Map;

class ExcessiveRangeCheck {
  public void test(int x, boolean b) {
    if(b && <warning descr="Can be replaced with 'x == 1'">x > 0 && x < 2</warning>) {}
    if(<warning descr="Can be replaced with 'x == 1'">x > 0 && x < 2</warning>) {}
    if(<warning descr="Can be replaced with 'x == 1'">x > 0 && x < 2</warning> && b) {}
    
    if(<warning descr="Can be replaced with 'x != 1'">x < 1 || 1 < x</warning>) {}
    if(b || <warning descr="Can be replaced with 'x != 1'">(x == 0) || x > 1 || x < 0</warning> || b) {}
    if(<warning descr="Can be replaced with 'x != 2'">!(x > 1) || x > 2</warning>) {}
  }

  void testHex(int x) {
    if (<warning descr="Can be replaced with 'x == 0x123B'">x > 0x123A && x < 0x123C</warning>) {}
  }

  void testLong(long l) {
    if (<warning descr="Can be replaced with 'l == 2'">l > 1 && l < 3</warning>) {}
    if (<warning descr="Can be replaced with 'l == 123456789013L'">l > 123456789012L && l < 123456789014L</warning>) {}
    if (<warning descr="Can be replaced with 'l == 0x4321432143214322L'">l > 0x4321432143214321L && l < 0x4321432143214323L</warning>) {}
  }
  
  public void testTwo(int x, int y) {
    if(<warning descr="Can be replaced with 'x == 1'">x > 0 && x < 2</warning> && <warning descr="Can be replaced with 'y == 1'">y > 0 && y < 2</warning>) {}
  }
  
  public void testArrayLength(int[] arr) {
    if(<warning descr="Can be replaced with 'arr.length != 1'">arr.length == 0 || arr.length > 1</warning>) {}
    if(<warning descr="Can be replaced with 'arr.length == 0'">arr.length < 1 && arr.length > -2</warning>) {}
  }

  public void testStringLength(String str) {
    if(<warning descr="Can be replaced with 'str.length() != 2'">str.length() == 0 || str.length() == 1 || str.length() > 2</warning>) {}
    if(<warning descr="Can be replaced with 'str.length() != 1'">str.isEmpty() || str.length() > 1</warning>) {}
  }

  public void testListSize(List<?> list) {
    if(<warning descr="Can be replaced with 'list.size() != 2'">list.size() == 0 || list.size() == 1 || list.size() > 2</warning>) {}
    if(<warning descr="Can be replaced with 'list.size() != 1'">list.isEmpty() || list.size() > 1</warning>) {}
  }

  public void testMapSize(Map<?,?> map) {
    if(<warning descr="Can be replaced with 'map.size() != 2'">map.size() == 0 || map.size() == 1 || map.size() > 2</warning>) {}
    if(<warning descr="Can be replaced with 'map.size() != 1'">map.isEmpty() || map.size() > 1</warning>) {}
  }
  
  public void testSideEffect() {
    if (get() == 0 && get() == 0) {
      
    }
  }

  
  native int get();
}