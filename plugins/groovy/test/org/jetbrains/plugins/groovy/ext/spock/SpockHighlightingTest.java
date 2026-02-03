// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyPointlessArithmeticInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyPointlessBooleanInspection;
import org.jetbrains.plugins.groovy.codeInspection.style.JavaStylePropertiesInvocationInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

public class SpockHighlightingTest extends SpockTestBase implements HighlightingTest {

  @Override
  public @NotNull Collection<Class<? extends LocalInspectionTool>> getInspections() {
    return Arrays.asList(
      GroovyAssignabilityCheckInspection.class,
      GroovyPointlessBooleanInspection.class,
      GroovyPointlessArithmeticInspection.class
    );
  }

  @Test
  public void singlePipeColumnSeparator() {
    highlightingTest("""
class FooSpec extends spock.lang.Specification {
  def feature(int a, boolean b) {
    where:
    a | b
    1 | true
    and:
    2 | false
  }
  
  def feature(int a) {
    where:
    a | _
    1 | _
    and:
    2 | _
  }
}
""");
  }

  @Test
  public void doublePipeColumnSeparator() {
    highlightingTest("""
class FooSpec extends spock.lang.Specification {
  
  def feature() {
    where:
    a    || false
    "hi" || false
  }
  
  def feature2() {
    where:
    a    || b
    true || false
  }
}
""");
  }

  @Test
  public void interactions() {
    enableInspectionAsWarning(new JavaStylePropertiesInvocationInspection());
    highlightingTest("""
interface Subscriber {
  def receive(String event)
  void setIncludeFollowUp(o)
}

class FooSpec extends spock.lang.Specification {

  def sub = Mock(Subscriber)

  def feature() {
    when:
    sub.receive("hi")

    then:
    1 * sub.receive(_)
    0 * sub.setIncludeFollowUp(1)
  }
}
""");
  }

  @Test
  public void dotInMethodName() {
    highlightingTest("""
class FooSpec extends spock.lang.Specification {
  def <warning descr="Method name contains illegal character(s): '.'">'not.a.feature'</warning>() {}
  def 'a.feature'() {
    when:
    42
  }
}
""");
  }

  @Test
  public void indirectExtending() {
    getFixture().enableInspections(new GrUnresolvedAccessInspection());
    highlightingTest("""
class A extends spock.lang.Specification {}

class FooSpec extends A {
  def foo() {
    when:
    def concat = a + b
    
    where:
    a     | b
    "foo" | "bar"
  }
}
""");
  }

  @Test
  public void useAnnotation() {
    getFixture().enableInspections(new GrUnresolvedAccessInspection());
    highlightingTest("""
import spock.lang.Specification
import spock.util.mop.Use

class A {
     static String foo(Integer self) {
        return "abs"
    }
}

@Use(A)
class HelloSpockSpec extends Specification {
    def "length of Spock's and his friends' names"() {
        expect:
        1.foo() == "abs"
    }
}
""");
  }
}