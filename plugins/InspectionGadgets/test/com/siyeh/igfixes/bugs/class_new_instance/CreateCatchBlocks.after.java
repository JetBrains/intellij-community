import java.lang.reflect.InvocationTargetException;

class High {
  void g(Class<?> x) {
    try {
        /*1*/
        x.getConstructor().newInstance();
    } catch (IllegalAccessException | InstantiationException e) {
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
        e.printStackTrace();
    } catch (InvocationTargetException e) {
        e.printStackTrace();
    }
  }
}