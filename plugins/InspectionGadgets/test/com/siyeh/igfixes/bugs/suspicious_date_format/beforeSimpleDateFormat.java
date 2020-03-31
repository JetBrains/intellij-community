// "Replace 'YYYY' with 'yyyy'" "true"
package java.text;

class SimpleDateFormat{public SimpleDateFormat(String pattern) {}}

class X {
  SimpleDateFormat format = new SimpleDateFormat("YY<caret>YY/MM/dd");
}