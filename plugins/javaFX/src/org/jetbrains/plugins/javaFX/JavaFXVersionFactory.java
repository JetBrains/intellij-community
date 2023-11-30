// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX;

import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;

public final class JavaFXVersionFactory extends ProjectTemplateParameterFactory {
  @Override
  public String getParameterId() {
    return "IJ_JAVAFX_VERSION";
  }

  @Override
  public @Nullable WizardInputField<?> createField(String defaultValue) {
    return new WizardInputField<>(getParameterId(), defaultValue) {
      @Override
      public String getLabel() {
        return null;
      }

      @Override
      public JComponent getComponent() {
        return null;
      }

      @Override
      public String getValue() {
        return null;
      }

      @Override
      public Map<String, String> getValues() {
        return Collections.emptyMap();
      }
    };
  }

  @Override
  public @Nullable String detectParameterValue(Project project) {
    return null;
  }


  @Override
  public void applyResult(String value, ModifiableRootModel model) {
    JavaSdkVersion version = getSdkVersion(model);
    if (version != null && version.isAtLeast(JavaSdkVersion.JDK_11)) {
      Module module = model.getModule();
      int preferredVersion = version.ordinal();
      ApplicationManager.getApplication().invokeLater(() -> {
        ExternalLibraryDescriptor libraryDescriptor =
          new ExternalLibraryDescriptor("org.openjfx", "javafx-fxml", "11", null, String.valueOf(preferredVersion));
        JavaProjectModelModificationService.getInstance(module.getProject())
          .addDependency(module, libraryDescriptor, DependencyScope.COMPILE);
      }, module.getDisposed());
    }
  }

  private static @Nullable JavaSdkVersion getSdkVersion(ModifiableRootModel model) {
    Sdk sdk = ProjectRootManager.getInstance(model.getProject()).getProjectSdk();
    if (sdk != null) {
      SdkTypeId type = sdk.getSdkType();
      if (type instanceof JavaSdk) {
        return ((JavaSdk)type).getVersion(sdk);
      }
    }
    return null;
  }
  
}
