class Lambda {{
  XYZ xyz = () -> String.class.getConstructor().newInstance();
}}
interface XYZ {
  void m() throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException;
}