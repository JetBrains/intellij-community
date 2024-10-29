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
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.SetupRule;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.transformations.impl.BindableTransformationSupport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

@RunWith(Parameterized.class)
public class GrBindableSupportTest {
  @Rule public SetupRule setupRule = new SetupRule();
  @Parameterized.Parameter public String name;
  @Parameterized.Parameter(1) public String clazzText;
  private PsiClass clazz;

  public JavaCodeInsightTestFixture getFixture() {
    return setupRule.getTestCase().getFixture();
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("@Bindable on class", "@groovy.beans.Bindable class Person {}");
    map.put("@Bindable on class @CompileStatic", "@groovy.transform.CompileStatic @groovy.beans.Bindable class Person {}");
    map.put("@Bindable on field", "class Person { @groovy.beans.Bindable foo }");
    map.put("@Bindable on field @CompileStatic", "@groovy.transform.CompileStatic class Person { @groovy.beans.Bindable foo }");
    return ContainerUtil.map(map.entrySet(), e -> new Object[]{e.getKey(), e.getValue()});
  }

  @Before
  public void addPerson() {
    getFixture().addFileToProject("Person.groovy", clazzText);
    clazz = getFixture().findClass("Person");
    Assert.assertNotNull(clazz);
  }

  @Test
  public void testGroovy() {
    List<String> groovyTextList = Arrays.asList("new Person().addPropertyChangeListener {}",
                                                "new Person().addPropertyChangeListener('') {}",
                                                "new Person().removePropertyChangeListener {}",
                                                "new Person().removePropertyChangeListener('') {}",
                                                "new Person().firePropertyChange('', null, null)",
                                                "new Person().getPropertyChangeListeners()",
                                                "new Person().getPropertyChangeListeners('')");
    for (String text : groovyTextList) {
      GroovyFile file = (GroovyFile) getFixture().configureByText("test.groovy", text);
      GrStatement[] statements = file.getStatements();
      GrCall call = (GrCall)statements[statements.length - 1];
      LightMethodBuilder method = (LightMethodBuilder) call.resolveMethod();
      Assert.assertNotNull(method);
      Assert.assertEquals(method.getContainingClass(), clazz);
      Assert.assertEquals(method.getOriginInfo(), BindableTransformationSupport.ORIGIN_INFO);
      Assert.assertNotNull(method.getUserData(ResolveUtil.DOCUMENTATION_DELEGATE_FQN));
    }
  }

  @Test
  public void testJava() {
    List<String> javaTextList = Arrays.asList("new Person().addPropertyChangeListener(e -> {});",
                                              "new Person().addPropertyChangeListener(\"\", e -> {});",
                                              "new Person().removePropertyChangeListener(e -> {});",
                                              "new Person().removePropertyChangeListener(\"\", e -> {});",
                                              "new Person().firePropertyChange(\"\", null, null);",
                                              "new Person().getPropertyChangeListeners();",
                                              "new Person().getPropertyChangeListeners(\"\");");

    for (String text : javaTextList) {
      PsiJavaFile file = (PsiJavaFile)getFixture().configureByText("Main.java", "class Main { void bar() {" + text + "} }");
      PsiExpressionStatement statement =  (PsiExpressionStatement) file.getClasses()[0].getMethods()[0].getBody().getStatements()[0];
      PsiCall call = (PsiCall) statement.getExpression();
      LightMethodBuilder method = (LightMethodBuilder) call.resolveMethod();
      Assert.assertNotNull(method);
      Assert.assertEquals(method.getContainingClass(), clazz);
      Assert.assertEquals(BindableTransformationSupport.ORIGIN_INFO, method.getOriginInfo());
    }
  }
}
