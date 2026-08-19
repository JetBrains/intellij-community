import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;

public class <error descr="Test project descriptor 'Flagged' does not override 'equals()' and 'hashCode()', so its non-static instances are not reused between tests">Flagged</error> extends LightProjectDescriptor {
}

class <error descr="Test project descriptor 'FlaggedBuilder' does not override 'equals()' and 'hashCode()', so its non-static instances are not reused between tests">FlaggedBuilder</error> extends LightProjectDescriptor {
  FlaggedBuilder withFoo() {
    return this;
  }
}

class <error descr="Test project descriptor 'FlaggedSubclassOfDefault' does not override 'equals()' and 'hashCode()', so its non-static instances are not reused between tests">FlaggedSubclassOfDefault</error> extends DefaultLightProjectDescriptor {
}

class <error descr="Test project descriptor 'OnlyEquals' does not override 'equals()' and 'hashCode()', so its non-static instances are not reused between tests">OnlyEquals</error> extends LightProjectDescriptor {
  @Override
  public boolean equals(Object o) {
    return o instanceof OnlyEquals;
  }
}
