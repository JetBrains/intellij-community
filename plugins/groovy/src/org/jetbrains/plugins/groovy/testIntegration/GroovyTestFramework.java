/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.testIntegration;

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.util.IncorrectOperationException;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import javax.swing.*;

/**
 * @author Max Medvedev
 */
public class GroovyTestFramework extends JavaTestFramework {
  private static final Logger LOG = Logger.getInstance(GroovyTestFramework.class);

  @Override
  protected String getMarkerClassFQName() {
    return GroovyCommonClassNames.GROOVY_UTIL_TEST_CASE;
  }

  @Override
  protected boolean isTestClass(PsiClass clazz, boolean canBePotential) {
    return clazz.getLanguage() == GroovyLanguage.INSTANCE &&
           //JUnitUtil.isTestClass(clazz) &&
           InheritanceUtil.isInheritor(clazz, GroovyCommonClassNames.GROOVY_UTIL_TEST_CASE);
  }

  @Override
  protected PsiMethod findSetUpMethod(@NotNull PsiClass clazz) {
    if (!isTestClass(clazz, false)) return null;

    for (PsiMethod method : clazz.getMethods()) {
      if (method.getName().equals("setUp")) return method;
    }
    return null;
  }

  @Override
  protected PsiMethod findTearDownMethod(@NotNull PsiClass clazz) {
    if (!isTestClass(clazz, false)) return null;

    for (PsiMethod method : clazz.getMethods()) {
      if (method.getName().equals("tearDown")) return method;
    }
    return null;
  }

  @Override
  protected PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException {
    LOG.assertTrue(clazz.getLanguage() == GroovyLanguage.INSTANCE);
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(clazz.getProject());

    final PsiMethod patternMethod = createSetUpPatternMethod(factory);

    final PsiClass baseClass = clazz.getSuperClass();
    if (baseClass != null) {
      final PsiMethod baseMethod = baseClass.findMethodBySignature(patternMethod, false);
      if (baseMethod != null && baseMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
        PsiUtil.setModifierProperty(patternMethod, PsiModifier.PROTECTED, false);
        PsiUtil.setModifierProperty(patternMethod, PsiModifier.PUBLIC, true);
      }
    }

    PsiMethod inClass = clazz.findMethodBySignature(patternMethod, false);
    if (inClass == null) {
      PsiMethod testMethod = JUnitUtil.findFirstTestMethod(clazz);
      if (testMethod != null) {
        return (PsiMethod)clazz.addBefore(patternMethod, testMethod);
      }
      return (PsiMethod)clazz.add(patternMethod);
    }
    else if (inClass.getBody() == null) {
      return (PsiMethod)inClass.replace(patternMethod);
    }
    return inClass;
  }

  @Override
  public char getMnemonic() {
    return 'G';
  }

  @NotNull
  @Override
  public String getName() {
    return "Groovy JUnit";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @Override
  public String getLibraryPath() {
    return GroovyFacetUtil.getBundledGroovyJar().getAbsolutePath();
  }

  @Override
  public String getDefaultSuperClass() {
    return GroovyCommonClassNames.GROOVY_UTIL_TEST_CASE;
  }

  @Override
  public FileTemplateDescriptor getTestClassFileTemplateDescriptor() {
    return new FileTemplateDescriptor(GroovyTemplates.GROOVY_JUNIT_TEST_CASE_GROOVY);
  }

  @Override
  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor(GroovyTemplates.GROOVY_JUNIT_SET_UP_METHOD_GROOVY);
  }

  @Override
  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor(GroovyTemplates.GROOVY_JUNIT_TEAR_DOWN_METHOD_GROOVY);
  }

  @Override
  public FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor(GroovyTemplates.GROOVY_JUNIT_TEST_METHOD_GROOVY);
  }

  @Override
  public boolean isTestMethod(PsiElement element) {
    return element instanceof PsiMethod && JUnitUtil.getTestMethod(element) != null;
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
