import org.jetbrains.annotations.Nls;

@Nls(capitalization = Nls.Capitalization.Sentence)
@interface ProgressText { }

interface Super {
  void get(@ProgressText String text);
}

class SuperCls {
  void consume(@ProgressText String s) {}
}

class MyClass extends SuperCls implements Super {
  @Override
  public void get(String text) {
    consume(text);
  }
}
