class CallToSuspiciousStringMethod {

  void m(String a, String b) {
    a.<warning descr="'String.equals()' called in internationalized context">equals</warning>(b);
    a.<warning descr="'String.equalsIgnoreCase()' called in internationalized context">equalsIgnoreCase</warning>(b);
    a.<warning descr="'String.compareTo()' called in internationalized context">compareTo</warning>(b);
    a.<warning descr="'String.compareToIgnoreCase()' called in internationalized context">compareToIgnoreCase</warning>(b);
    a.<warning descr="'String.trim()' called in internationalized context">trim</warning>();
  }

  @SuppressWarnings({"CallToStringCompareTo", "CallToStringEquals", "CallToStringEqualsIgnoreCase"})
  void n(String a, String b) {
    a.equals(b);
    a.equalsIgnoreCase(b);
    a.compareTo(b);
    //noinspection CallToSuspiciousStringMethod
    a.compareToIgnoreCase(b);
  }
}