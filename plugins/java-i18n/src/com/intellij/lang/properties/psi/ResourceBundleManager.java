// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.psi;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class ResourceBundleManager {
  private static final ExtensionPointName<ResourceBundleManager> RESOURCE_BUNDLE_MANAGER = ExtensionPointName.create("com.intellij.java-i18n.resourceBundleManager");
  protected final Project myProject;

  protected ResourceBundleManager(final Project project) {
    myProject = project;
  }

  /**
   * By default returns java.util.ResourceBundle class in context JDK
   */
  public abstract @Nullable PsiClass getResourceBundle();

  public List<String> suggestPropertiesFiles(@NotNull Set<Module> contextModules){
    return I18nUtil.defaultSuggestPropertiesFiles(myProject, contextModules);
  }

  public @Nullable I18nizedTextGenerator getI18nizedTextGenerator() {
    return null;
  }

  public abstract @Nullable @NonNls String getTemplateName();

  public abstract @Nullable @NonNls String getConcatenationTemplateName();

  public abstract boolean isActive(@NotNull PsiFile context) throws ResourceBundleNotFoundException;

  public abstract boolean canShowJavaCodeInfo();
  
  public String escapeValue(String value) {
    return value;
  }

  public static @Nullable ResourceBundleManager getManager(@NotNull PsiFile context) throws ResourceBundleNotFoundException {
    return getManager(Collections.singletonList(context), context.getProject());
  }

  public static @Nullable ResourceBundleManager getManager(@NotNull Collection<PsiFile> contexts, @NotNull Project project) throws ResourceBundleNotFoundException {
    ResourceBundleManager result = null;
    for (ResourceBundleManager manager : RESOURCE_BUNDLE_MANAGER.getExtensions(project)) {
      if (isActiveForAny(manager, contexts)) {
        if (result != null) {
          //multiple managers are active
          return null;
        }
        result = manager;
      }
    }
    if (result != null) {
      return result;
    }
    final DefaultResourceBundleManager manager = new DefaultResourceBundleManager(project);
    return isActiveForAny(manager, contexts) ? manager : null;
  }

  private static boolean isActiveForAny(ResourceBundleManager manager, Collection<PsiFile> contexts) throws ResourceBundleNotFoundException {
    for (PsiFile context : contexts) {
      if (manager.isActive(context)) {
        return true;
      }
    }
    return false;
  }

  public @Nullable PropertyCreationHandler getPropertyCreationHandler() {
    return null;
  }

  public @Nullable String suggestPropertyKey(final @NotNull String value) {
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
