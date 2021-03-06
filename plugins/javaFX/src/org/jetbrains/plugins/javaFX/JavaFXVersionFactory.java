// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public class JavaFXVersionFactory extends ProjectTemplateParameterFactory {
  @Override
  public String getParameterId() {
    return "IJ_JAVAFX_VERSION";
  }

  @Nullable
  @Override
  public WizardInputField<?> createField(String defaultValue) {
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

  @Nullable
  @Override
  public String detectParameterValue(Project project) {
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

  @Nullable
  private static JavaSdkVersion getSdkVersion(ModifiableRootModel model) {
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
