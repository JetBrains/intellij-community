// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.ide.util.ClassFilter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.plugins.javaFX.JavaFXBundle;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

public class JavaFxApplicationClassBrowser extends ClassBrowser<JTextField> {
  private final String myApplicationClass;
  private final Artifact myArtifact;

  public JavaFxApplicationClassBrowser(Project project, @NlsContexts.DialogTitle String title, String applicationClass, Artifact artifact) {
    super(project, title);
    myArtifact = artifact;
    myApplicationClass = applicationClass;
  }

  @Override
  protected ClassFilter.ClassFilterWithScope getFilter() {
    return new ClassFilter.ClassFilterWithScope() {
      @Override
      public GlobalSearchScope getScope() {
        return GlobalSearchScope.projectScope(getProject());
      }

      @Override
      public boolean isAccepted(PsiClass aClass) {
        return InheritanceUtil.isInheritor(aClass, myApplicationClass);
      }
    };
  }

  @Override
  protected PsiClass findClass(String className) {
    Set<Module> modules = ReadAction.compute(() -> ArtifactUtil.getModulesIncludedInArtifacts(Collections.singletonList(myArtifact), getProject()));
    for (Module module : modules) {
      PsiClass aClass = JavaExecutionUtil.findMainClass(getProject(), className, GlobalSearchScope.moduleScope(module));
      if (aClass != null) {
        return aClass;
      }
    }
    return null;
  }

  public static JavaFxApplicationClassBrowser appClassBrowser(Project project, Artifact artifact) {
    return new JavaFxApplicationClassBrowser(project, JavaFXBundle.message("dialog.title.choose.application.class"), "javafx.application.Application", artifact);
  }

  public static JavaFxApplicationClassBrowser preloaderClassBrowser(Project project, Artifact artifact) {
    return new JavaFxApplicationClassBrowser(project, JavaFXBundle.message("dialog.title.choose.preloader.class"), "javafx.application.Preloader", artifact);
  }
}