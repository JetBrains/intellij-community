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
package org.jetbrains.plugins.groovy.dgm;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_INSTANCE_EXTENSIONS;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_STATIC_EXTENSIONS;

/**
 * @author Max Medvedev
 */
public class GroovyExtensionProvider {
  @NonNls public static final String ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE = "org.codehaus.groovy.runtime.ExtensionModule";
  private final Project myProject;

  public GroovyExtensionProvider(Project project) {
    myProject = project;
  }

  public static GroovyExtensionProvider getInstance(Project project) {
    return ServiceManager.getService(project, GroovyExtensionProvider.class);
  }

  public Couple<List<String>> collectExtensions(@NotNull GlobalSearchScope resolveScope) {
    List<String> instanceClasses = ContainerUtil.newArrayList(DEFAULT_INSTANCE_EXTENSIONS);
    List<String> staticClasses = ContainerUtil.newArrayList(DEFAULT_STATIC_EXTENSIONS);
    doCollectExtensions(resolveScope, instanceClasses, staticClasses);
    return Couple.of(instanceClasses, staticClasses);
  }

  private void doCollectExtensions(@NotNull GlobalSearchScope resolveScope, List<String> instanceClasses, List<String> staticClasses) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage("META-INF.services");
    if (aPackage == null) return;

    for (PsiDirectory directory : aPackage.getDirectories(resolveScope)) {
      PsiFile file = directory.findFile(ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE);
      if (file instanceof PropertiesFile) {
        IProperty inst = ((PropertiesFile)file).findPropertyByKey("extensionClasses");
        IProperty stat = ((PropertiesFile)file).findPropertyByKey("staticExtensionClasses");

        if (inst != null) collectClasses(inst, instanceClasses);
        if (stat != null) collectClasses(stat, staticClasses);
      }
    }
  }

  private static void collectClasses(IProperty pr, List<String> classes) {
    String value = pr.getUnescapedValue();
    if (value == null) return;
    value = value.trim();
    String[] qnames = value.split("\\s*,\\s*");
    ContainerUtil.addAll(classes, qnames);
  }

  public static class GroovyExtensionVetoSPI implements Condition<String> {

    @Override
    public boolean value(String s) {
      return ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE.equals(s);
    }
  }
}
