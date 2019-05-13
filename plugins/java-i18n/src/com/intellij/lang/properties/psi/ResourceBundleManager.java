/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties.psi;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

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
    final ResourceBundleManager[] managers = project.getExtensions(RESOURCE_BUNDLE_MANAGER);
    for (ResourceBundleManager manager : managers) {
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
