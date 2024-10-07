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

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.SetupRule;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.transformations.impl.VetoableTransformationSupportKt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

@RunWith(Parameterized.class)
public class GrVetoableSupportTest {
  public JavaCodeInsightTestFixture getFixture() {
    return setupRule.getTestCase().getFixture();
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("@Vetoable on class", "@groovy.beans.Vetoable class Person {}");
    map.put("@Vetoable on class @CompileStatic", "@groovy.transform.CompileStatic @groovy.beans.Vetoable class Person {}");
    map.put("@Vetoable on field", "class Person { @groovy.beans.Vetoable foo }");
    map.put("@Vetoable on field @CompileStatic", "@groovy.transform.CompileStatic class Person { @groovy.beans.Vetoable foo }");
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
    getFixture().enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);

    List<String> groovyTextList = Arrays.asList("new Person().addVetoableChangeListener {}",
                                                "new Person().addVetoableChangeListener('') {}",
                                                "new Person().removeVetoableChangeListener {}",
                                                "new Person().removeVetoableChangeListener('') {}",
                                                "new Person().fireVetoableChange('', null, null)",
                                                "new Person().getVetoableChangeListeners()",
                                                "new Person().getVetoableChangeListeners('')");

    for (String text : groovyTextList) {
      GroovyFile file = (GroovyFile)getFixture().configureByText("test.groovy", text);
      GrStatement[] statements = file.getStatements();
      GrCall call = (GrCall)statements[statements.length - 1];
      GrLightMethodBuilder method = (GrLightMethodBuilder)call.resolveMethod();
      Assert.assertNotNull(method);
      Assert.assertEquals(method.getContainingClass(), clazz);
      Assert.assertEquals(method.getOriginInfo(), VetoableTransformationSupportKt.ORIGIN_INFO);
      Assert.assertNotNull(method.getUserData(ResolveUtil.DOCUMENTATION_DELEGATE_FQN));
      getFixture().checkHighlighting();
    }
  }

  @Test
  public void testJava() {
    IdeaTestUtil.setProjectLanguageLevel(getFixture().getProject(), LanguageLevel.HIGHEST);
    List<String> javaTextString = Arrays.asList("new Person().addVetoableChangeListener(e -> {});",
                                                "new Person().addVetoableChangeListener(\"\", e -> {});",
                                                "new Person().removeVetoableChangeListener(e -> {});",
                                                "new Person().removeVetoableChangeListener(\"\", e -> {});",
                                                "new Person().fireVetoableChange(\"\", null, null);",
                                                "new Person().getVetoableChangeListeners();",
                                                "new Person().getVetoableChangeListeners(\"\");");

    for (String text : javaTextString) {
      PsiJavaFile file = (PsiJavaFile)getFixture().configureByText("Main.java", "class Main { void bar() {" + text + "} }");

      PsiExpressionStatement statement = (PsiExpressionStatement) file.getClasses()[0].getMethods()[0].getBody().getStatements()[0];
      PsiCall call = (PsiCall)statement.getExpression();
      GrLightMethodBuilder method = (GrLightMethodBuilder)call.resolveMethod();
      Assert.assertNotNull(method);
      Assert.assertEquals(method.getContainingClass(), clazz);
      Assert.assertEquals(VetoableTransformationSupportKt.ORIGIN_INFO, method.getOriginInfo());
      getFixture().checkHighlighting();
    }
  }

  @Rule public SetupRule setupRule = new SetupRule();
  @Parameterized.Parameter public String name;
  @Parameterized.Parameter(1) public String clazzText;
  private PsiClass clazz;
}
