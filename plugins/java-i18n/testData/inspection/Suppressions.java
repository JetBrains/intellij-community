@SuppressWarnings("SpellCheckingInspection")
class BaddName1 {
  int baddField;
  void baddMethod() { }
}

@SuppressWarnings("ALL")
class BaddName2 {
  int baddField;
  void baddMethod() { }
}

class GoodName {
  @SuppressWarnings("SpellCheckingInspection") int baddField;
  @SuppressWarnings("SpellCheckingInspection") void baddMethod() { }
}
