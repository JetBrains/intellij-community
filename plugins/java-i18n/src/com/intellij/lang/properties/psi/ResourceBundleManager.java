// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.psi;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ResourceBundleManager {
  private static final ExtensionPointName<ResourceBundleManager> RESOURCE_BUNDLE_MANAGER = ExtensionPointName.create("com.intellij.java-i18n.resourceBundleManager");
  protected final Project myProject;

  protected ResourceBundleManager(final Project project) {
    myProject = project;
  }

  /**
   * By default returns java.util.ResourceBundle class in context JDK
   */
  @Nullable
  public abstract PsiClass getResourceBundle();

  public List<String> suggestPropertiesFiles(){
    return I18nUtil.defaultSuggestPropertiesFiles(myProject);
  }

  @Nullable
  public I18nizedTextGenerator getI18nizedTextGenerator() {
    return null;
  }

  @Nullable @NonNls
  public abstract String getTemplateName();

  @Nullable @NonNls
  public abstract String getConcatenationTemplateName();

  public abstract boolean isActive(PsiFile context) throws ResourceBundleNotFoundException;

  public abstract boolean canShowJavaCodeInfo();

  @Nullable
  public static ResourceBundleManager getManager(PsiFile context) throws ResourceBundleNotFoundException {
    final Project project = context.getProject();
    for (ResourceBundleManager manager : RESOURCE_BUNDLE_MANAGER.getExtensions(project)) {
      if (manager.isActive(context)) {
        return manager;
      }
    }
    final DefaultResourceBundleManager manager = new DefaultResourceBundleManager(project);
    return manager.isActive(context) ? manager : null;
  }

  @Nullable
  public PropertyCreationHandler getPropertyCreationHandler() {
    return null;
  }

  @Nullable
  public String suggestPropertyKey(@NotNull final String value) {
    return null;
  }

  public static class ResourceBundleNotFoundException extends Exception {
    private final IntentionAction myFix;

    public ResourceBundleNotFoundException(final String message, IntentionAction setupResourceBundle) {
      super(message);
      myFix = setupResourceBundle;
    }

    public IntentionAction getFix() {
      return myFix;
    }
  }
}
