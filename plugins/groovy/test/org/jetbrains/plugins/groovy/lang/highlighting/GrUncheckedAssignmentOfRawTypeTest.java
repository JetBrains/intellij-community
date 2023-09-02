package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.LocalInspectionTool;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyUncheckedAssignmentOfMemberOfRawTypeInspection;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class GrUncheckedAssignmentOfRawTypeTest extends GroovyLatestTest implements HighlightingTest {
  public GrUncheckedAssignmentOfRawTypeTest() {
    super("highlighting");
  }

  @Override
  public String getTestName() {
    return StringGroovyMethods.capitalize(super.getTestName());
  }

  @Test
  public void rawMethodAccess() { fileHighlightingTest(); }

  @Test
  public void rawFieldAccess() { fileHighlightingTest(); }

  @Test
  public void rawArrayStyleAccess() { fileHighlightingTest(); }

  @Test
  public void rawArrayStyleAccessToMap() { fileHighlightingTest(); }

  @Test
  public void rawArrayStyleAccessToList() { fileHighlightingTest(); }

  @Test
  public void rawClosureReturnType() {
    highlightingTest("""
class A<T> {
  A(T t) {this.t = t}

  T t
  def cl = {
    return t
  }
}


def a = new A(new Date())
Date d = <warning descr="Cannot assign 'Object' to 'Date'">a.cl()</warning>
""");
  }

  @Override
  public final @NotNull List<Class<? extends LocalInspectionTool>> getInspections() {
    return Collections.singletonList(GroovyUncheckedAssignmentOfMemberOfRawTypeInspection.class);
  }
}
