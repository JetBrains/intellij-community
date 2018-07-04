/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.testIntegration.GroovyTestFramework;

import static com.intellij.psi.util.InheritanceUtil.isInheritor;

/**
 * @author Sergey Evdokimov
 */
public class SpockTestFramework extends GroovyTestFramework {
  private static final ExternalLibraryDescriptor SPOCK_DESCRIPTOR = new ExternalLibraryDescriptor("org.spockframework", "spock-core");

  @NotNull
  @Override
  public String getName() {
    return "Spock";
  }

  @Override
  public String getLibraryPath() {
    return null;
  }

  @Override
  public ExternalLibraryDescriptor getFrameworkLibraryDescriptor() {
    return SPOCK_DESCRIPTOR;
  }

  @Nullable
  @Override
  public String getDefaultSuperClass() {
    return SpockUtils.SPEC_CLASS_NAME;
  }

  @Override
  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("Spock_SetUp_Method.groovy");
  }

  @Override
  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("Spock cleanup Method.groovy");
  }

  @NotNull
  @Override
  public FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("Spock Test Method.groovy");
  }

  @Override
  public boolean isTestMethod(PsiElement element, boolean checkAbstract) {
    return SpockUtils.isTestMethod(element);
  }

  @Override
  protected String getMarkerClassFQName() {
    return SpockUtils.SPEC_CLASS_NAME;
  }

  @Override
  protected boolean isTestClass(PsiClass clazz, boolean canBePotential) {
    return clazz.getLanguage() == GroovyLanguage.INSTANCE && isInheritor(clazz, SpockUtils.SPEC_CLASS_NAME);
  }

  private PsiMethod findSpecificMethod(@NotNull PsiClass clazz, String methodName) {
    if (!isTestClass(clazz, false)) return null;

    for (PsiMethod method : clazz.findMethodsByName(methodName, false)) {
      if (method.getParameterList().isEmpty()) return method;
    }

    return null;
  }

  @Nullable
  @Override
  protected PsiMethod findSetUpMethod(@NotNull PsiClass clazz) {
    return findSpecificMethod(clazz, SpockConstants.SETUP_METHOD_NAME);
  }

  @Nullable
  @Override
  protected PsiMethod findTearDownMethod(@NotNull PsiClass clazz) {
    return findSpecificMethod(clazz, SpockConstants.CLEANUP_METHOD_NAME);
  }

  @Override
  public char getMnemonic() {
    return 'S';
  }
}
