// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactTemplate;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.ui.ArtifactProblemsHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.JavaFXCommonBundle;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JavaFxApplicationArtifactType extends ArtifactType {
  public static JavaFxApplicationArtifactType getInstance() {
    return EP_NAME.findExtension(JavaFxApplicationArtifactType.class);
  }
  
  private JavaFxApplicationArtifactType() {
    super("javafx", JavaFXBundle.messagePointer("javafx.application.title"));
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Nodes.Artifact;
  }

  @Override
  public @Nullable String getDefaultPathFor(@NotNull PackagingElementOutputKind kind) {
    return "/";
  }

  @Override
  public @NotNull CompositePackagingElement<?> createRootElement(@NotNull String artifactName) {
    return PackagingElementFactory.getInstance().createArtifactRootElement();
  }

  @Override
  public void checkRootElement(@NotNull CompositePackagingElement<?> rootElement,
                               @NotNull Artifact artifact,
                               @NotNull ArtifactProblemsHolder manager) {
    final Project project = manager.getContext().getProject();
    final Sdk sdk = JavaFxArtifactProperties.getFxCompatibleSdk(artifact, project);
    if (sdk != null && !new File(sdk.getHomePath() + "/lib/ant-javafx.jar").exists()) {
      manager.registerError(JavaFXCommonBundle.message("cant.build.artifact.fx.deploy.is.not.available.in.this.jdk"), "no-fx:deploy");
    }
  }

  @Override
  public @NotNull List<? extends ArtifactTemplate> getNewArtifactTemplates(@NotNull PackagingElementResolvingContext context) {
    final List<Module> modules = new ArrayList<>();
    for (Module module : context.getModulesProvider().getModules()) {
      if (ModuleType.get(module) instanceof JavaModuleType) {
        modules.add(module);
      }
    }
    if (modules.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new JavaFxArtifactTemplate(modules));
  }

  private final class JavaFxArtifactTemplate extends ArtifactTemplate {
    private final List<Module> myModules;

    JavaFxArtifactTemplate(List<Module> modules) {
      myModules = modules;
    }

    @Override
    public String getPresentableName() {
      if (myModules.size() == 1) {
        return JavaFXBundle.message("action.from.modules.artifact.text", myModules.get(0).getName());
      }
      return JavaFXBundle.message("action.from.module.artifact.text");
    }

    @Override
    public NewArtifactConfiguration createArtifact() {
      Module module = null;
      if (myModules.size() == 1) {
        module = myModules.get(0);
      } else {
        final ChooseModulesDialog dialog = new ChooseModulesDialog(myModules.get(0).getProject(), myModules,
                                                                   JavaFXBundle.message("dialog.title.select.module.for.artifact"),
                                                                   JavaFXBundle.message(
                                                                     "label.selected.module.output.would.to.be.included.in.artifact"));
        dialog.setSingleSelectionMode();
        if (dialog.showAndGet()) {
          final List<Module> elements = dialog.getChosenElements();
          if (elements.isEmpty()) {
            return null;
          }
          module = elements.get(0);
        }
      }
      if (module == null) return null;
      final CompositePackagingElement<?> rootElement = JavaFxApplicationArtifactType.this.createRootElement(module.getName());
      final CompositePackagingElement<?>
        subElement = PackagingElementFactory.getInstance().createArchive(FileUtil.sanitizeFileName(module.getName()) + ".jar");
      final PackagingElement<?> moduleOutputElement = PackagingElementFactory.getInstance().createModuleOutput(module);
      subElement.addFirstChild(moduleOutputElement);
      rootElement.addFirstChild(subElement);
      return new NewArtifactConfiguration(rootElement, module.getName(), JavaFxApplicationArtifactType.this);
    }
  }
}
