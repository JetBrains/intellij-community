class Lambda {{
  XYZ xyz = () -> //end of line comment
          String.class.getConstructor().newInstance();
}}
interface XYZ {
  void m() throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException;
}