class Lambda {{
  XYZ xyz = () -> String.class.<caret>newInstance();
}}
interface XYZ {
  void m() throws InstantiationException, IllegalAccessException;
}