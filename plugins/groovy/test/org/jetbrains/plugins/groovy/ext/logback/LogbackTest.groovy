/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.ext.logback

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.pom.PomTargetPsiElement
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

@CompileStatic
class LogbackTest extends LightGroovyTestCase {

  LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.with {
      addClass '''
package ch.qos.logback.classic;
public interface Level {
  Level ERROR = null;
  Level INFO = null;
  Level WARN = null;
}
'''
      addClass '''
package ch.qos.logback.classic.gaffer;
import ch.qos.logback.classic.Level;
public class ConfigurationDelegate {
  void scan(String scanPeriodStr) {}
  void statusListener(Class listenerClass) {}
  void conversionRule(String conversionWord, Class converterClass) {}
  void root(Level level) {}
  void root(Level level, List<String> appenderNames) {}
  void logger(String name, Level level) {}
  void logger(String name, Level level, List<String> appenderNames) {}
  void logger(String name, Level level, List<String> appenderNames, Boolean b) {}
  void appender(String name, Class clazz) {}
  void appender(String name, Class clazz, Closure closure) {}
  void receiver(String name, Class aClass) {}
  void receiver(String name, Class aClass, Closure closure) {}
  void turboFilter(Class clazz) {}
  void turboFilter(Class clazz, Closure closure) {}
  String timestamp(String datePattern, long timeReference) {}
  void jmxConfigurator() {}
  void jmxConfigurator(String name) {}
}
'''
      addClass '''
package ch.qos.logback.classic.gaffer;
public class ComponentDelegate {}
'''
      addClass '''
package ch.qos.logback.classic.gaffer;
public class AppenderDelegate extends ComponentDelegate {}
'''
      addClass '''
package ch.qos.logback.core.encoder;
public interface Encoder {}
'''
      addClass '''
package ch.qos.logback.classic.encoder;
public interface PatternLayoutEncoder extends ch.qos.logback.core.encoder.Encoder {
  String getPattern();
  void setPattern(String s);
}
'''
      addClass '''
package ch.qos.logback.core;
import ch.qos.logback.core.encoder.Encoder;
public interface ConsoleAppender {
  Encoder getEncoder();
  void setEncoder(Encoder e);
}
'''
      addClass '''
package ch.qos.logback.core;
import ch.qos.logback.core.encoder.Encoder;
public interface FileAppender {
  Encoder getEncoder();
  void setEncoder(Encoder e);
  boolean isAppend();
  void setAppend(boolean a);
  void setFile(String file);
}
'''
    }
  }

  void 'test highlighting'() {
    fixture.with {
      configureByText 'logback.groovy', '''\
appender("FULL_STACKTRACE", FileAppender) {
    file = "./stacktrace.log"
    append = true
    <warning descr="Cannot resolve symbol 'setAppend'">setAppend</warning>(true)
    encoder(PatternLayoutEncoder) {
        pattern = "%level %logger - %msg%n"
    }
    <warning descr="Cannot resolve symbol 'encoder'">encoder</warning>
    <warning descr="Cannot resolve symbol 'setEncoder'">setEncoder</warning>(new Encoder(){})
}

logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false)
root(warn, [''])
'''
      enableInspections(GrUnresolvedAccessInspection)
      checkHighlighting()
    }
  }

  void 'test component delegate completion'() {
    fixture.with {
      configureByText 'logback.groovy', '''\
appender("FULL_STACKTRACE", FileAppender) {
    <caret>
}

logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false)
'''
      completeBasic()
      def lookupStrings = lookupElementStrings.toSet()
      ['file', 'append', 'encoder'].each {
        assert it in lookupStrings
      }
    }
  }

  void 'test target completion'() {
    fixture.configureByText 'logback.groovy', '''\
appender('FOO_APP', ConsoleAppender)
appender("BAR_APP", FileAppender) {}
logger("", ERROR, ['<caret>'])
'''
    fixture.completeBasic()
    assert ['FOO_APP', 'BAR_APP'].toSet() == fixture.lookupElementStrings.toSet()
  }

  void 'test target navigation'() {
    fixture.with {
      configureByText 'logback.groovy', '''\
appender('FOO_APP', ConsoleAppender)
appender("BAR_APP", FileAppender) {}
logger("", ERROR, ['FOO<caret>_APP'])
'''
      performEditorAction 'GotoDeclaration'
      checkResult '''\
appender('<caret>FOO_APP', ConsoleAppender)
appender("BAR_APP", FileAppender) {}
logger("", ERROR, ['FOO_APP'])
'''
    }
  }

  void 'test element to find usages of exists'() {
    fixture.with {
      configureByText 'logback.groovy', '''\
appender('FOO_<caret>APP', ConsoleAppender)
'''
      def targetElement = GotoDeclarationAction.findElementToShowUsagesOf(editor, caretOffset) as PomTargetPsiElement
      assert targetElement
      assert targetElement.target instanceof AppenderTarget
    }
  }
}
