import com.intellij.testFramework.LightProjectDescriptor;

public class NotFlagged extends LightProjectDescriptor {
  @Override
  public boolean equals(Object o) {
    return o instanceof NotFlagged;
  }

  @Override
  public int hashCode() {
    return 1;
  }
}

class SharedInstanceDescriptor extends LightProjectDescriptor {
  static final SharedInstanceDescriptor INSTANCE = new SharedInstanceDescriptor();
}

abstract class AbstractDescriptor extends LightProjectDescriptor {
}

class InheritsEqualsAndHashCode extends NotFlagged {
}

class Unrelated {
}
