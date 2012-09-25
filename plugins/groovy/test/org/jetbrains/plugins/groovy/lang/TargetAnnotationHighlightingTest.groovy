/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author Max Medvedev
 */
class TargetAnnotationHighlightingTest extends LightCodeInsightFixtureTestCase {
  final LightProjectDescriptor projectDescriptor = LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR

  private void addElementType() {
    myFixture.addClass('''\
package java.lang.annotation;

public enum ElementType {
    /** Class, interface (including annotation type), or enum declaration */
    TYPE,

    /** Field declaration (includes enum constants) */
    FIELD,

    /** Method declaration */
    METHOD,

    /** Parameter declaration */
    PARAMETER,

    /** Constructor declaration */
    CONSTRUCTOR,

    /** Local variable declaration */
    LOCAL_VARIABLE,

    /** Annotation type declaration */
    ANNOTATION_TYPE,

    /** Package declaration */
    PACKAGE
}
''')
  }

  private void addTarget() {
    myFixture.addClass('''\
package java.lang.annotation;

public @interface Target {
    ElementType[] value();
}
''')
  }

  public void testTargetAnnotationInsideGroovy1() {
    addElementType()
    addTarget()
    myFixture.addFileToProject('Ann.groovy', '''
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.*

@Target(FIELD)
@interface Ann {}
''')

    myFixture.configureByText('_.groovy', '''\
@<error descr="'@Ann' not applicable to type">Ann</error>
class C {
  @Ann
  def foo

  def ar() {
    @<error descr="'@Ann' not applicable to local variable">Ann</error>
    def x
  }
}''')

    myFixture.testHighlighting(true, false, false)
  }

  public void testTargetAnnotationInsideGroovy2() {
    addElementType()
    addTarget()
    myFixture.addFileToProject('Ann.groovy', '''
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.*

@Target(value=[FIELD, TYPE])
@interface Ann {}
''')

    myFixture.configureByText('_.groovy', '''\
@Ann
class C {
  @Ann
  def foo

  def ar() {
    @<error descr="'@Ann' not applicable to local variable">Ann</error>
    def x
  }
}''')
    myFixture.testHighlighting(true, false, false)
  }

  public void testTargetAnnotationInsideGroovy3() {
    addElementType()
    addTarget()
    myFixture.addFileToProject('Ann.groovy', '''
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.*

@Target(LOCAL_VARIABLE)
@interface Ann {}
''')

    myFixture.configureByText('_.groovy', '''\
@<error descr="'@Ann' not applicable to type">Ann</error>
class C {
  @<error descr="'@Ann' not applicable to field">Ann</error>
  def foo

  def ar() {
    @Ann
    def x
  }
}''')
    myFixture.testHighlighting(true, false, false)
  }
}
