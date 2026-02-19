package org.jetbrains.plugins.groovy.util;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

public abstract class Groovy30Test extends LightProjectTest {
  @Override
  public final LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_3_0;
  }
}
