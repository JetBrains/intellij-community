import java.lang.reflect.InvocationTargetException;

class High {
  void g(Class<?> x) {
    try {
        /*1*/
        x.getConstructor().newInstance();
    } catch (IllegalAccessException | InstantiationException e) {
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
    }
  }
}