class StringCategory {
  static String getMd5(String s) {
    return "MD5Value";//suppose to be some code that generate md5
  }
}

class StringCategory2 {
  static String getMd52(String s) {
    return "MD5Value";
  }
}


def categories = [StringCategory, StringCategory2]
use(categories) {
  println "hello world".getMd<caret>
}

