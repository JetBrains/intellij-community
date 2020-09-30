import org.jetbrains.annotations.Nls;

class MyTest {
  @Nls
  public String getColumnName2(int column) {
    return <warning descr="Reference to non-localized string is used where localized string is expected">getCol(column).getLocalizedName()</warning>;
  }

  static native Column<?> getCol(int idx);

  interface Column<T> {
    String getLocalizedName();
  }
}