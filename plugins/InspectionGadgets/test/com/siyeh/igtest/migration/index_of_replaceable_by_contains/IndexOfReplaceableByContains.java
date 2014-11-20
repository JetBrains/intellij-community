class IndexOfReplaceableByContains {

  void m(String haystack, String needle) {
    boolean a = <warning descr="'haystack.indexOf(needle) == -1' can be replaced with '!haystack.contains(needle)'">haystack.indexOf(needle) == -1</warning>;
    boolean b = <warning descr="'haystack.indexOf(needle) <= -1' can be replaced with '!haystack.contains(needle)'">haystack.indexOf(needle) <= -1</warning>;
    boolean c = <warning descr="'haystack.indexOf(needle) < 0' can be replaced with '!haystack.contains(needle)'">haystack.indexOf(needle) < 0</warning>;

    boolean d = <warning descr="'haystack.indexOf(needle) > -1' can be replaced with 'haystack.contains(needle)'">haystack.indexOf(needle) > -1</warning>;
    boolean e = <warning descr="'haystack.indexOf(needle) >= 0' can be replaced with 'haystack.contains(needle)'">haystack.indexOf(needle) >= 0</warning>;
  }
}