import java.text.*;

class Test {
  void test(boolean b) {
    new SimpleDateFormat("<warning descr="Upper-cased 'YYYY' (week year) pattern is used: probably 'yyyy' (year) was intended">YYYY</warning>");
    new SimpleDateFormat("ww/YYYY");
    new SimpleDateFormat("<warning descr="Upper-cased 'YYYY' (week year) pattern is used: probably 'yyyy' (year) was intended">YYYY</warning>-MM-<warning descr="Upper-cased 'DD' (day of year) pattern is used: probably 'dd' (day of month) was intended">DD</warning>");
    new SimpleDateFormat("yyyy-MM-<warning descr="Upper-cased 'DD' (day of year) pattern is used: probably 'dd' (day of month) was intended">DD</warning>");
    new SimpleDateFormat("yyyy-MM-dd");
    new SimpleDateFormat("yyyy-<warning descr="Lower-cased 'mm' (minute) pattern is used: probably 'MM' (month) was intended">mm</warning>-dd");
    new SimpleDateFormat("yyyy-DD");
    new SimpleDateFormat(b ? "HH:mm:ss" : "HH:<warning descr="Upper-cased 'MM' (month) pattern is used: probably 'mm' (minute) was intended">MM</warning>");
    new SimpleDateFormat("HH:<warning descr="Upper-cased 'MM' (month) pattern is used: probably 'mm' (minute) was intended">MM</warning>:SS");
    new SimpleDateFormat("HH:mm:<warning descr="Upper-cased 'SS' (milliseconds) pattern is used: probably 'ss' (seconds) was intended">SS</warning>");
    new SimpleDateFormat("HH:mm:ss");
  }
}