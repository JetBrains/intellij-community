// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.packaging.jpackage;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactTemplate;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.ui.ArtifactProblemsHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFXBundle;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class JPackageArtifactType extends ArtifactType {

  private JPackageArtifactType() {
    super("jpackage", () -> "Platform specific package");
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
    final Sdk sdk = getJPackageCompatibleSdk(artifact, project);
    if (sdk == null) {
      manager.registerError("Needs at least JDK 14 containing the jpackage tool", "no-jpackage");
    }
  }

  public static Sdk getJPackageCompatibleSdk(Artifact artifact, Project project) {
    final Set<Module> modules =
      ReadAction.compute(() -> ArtifactUtil.getModulesIncludedInArtifacts(Collections.singletonList(artifact), project));
    if (modules.isEmpty()) {
      return null;
    }

    for (Module module : modules) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null) {
        final SdkTypeId sdkType = sdk.getSdkType();
        if (sdkType instanceof JavaSdk && ((JavaSdk)sdkType).isOfVersionOrHigher(sdk, JavaSdkVersion.JDK_14)) {
          return sdk;
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public List<? extends ArtifactTemplate> getNewArtifactTemplates(@NotNull PackagingElementResolvingContext context) {
    final List<Module> modules = new ArrayList<>();
    for (Module module : context.getModulesProvider().getModules()) {
      if (ModuleType.get(module) instanceof JavaModuleType) {
        modules.add(module);
      }
    }
    if (modules.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new JPackageArtifactType.JPackageArtifactTemplate(modules));
  }

  private class JPackageArtifactTemplate extends ArtifactTemplate {
    private final List<Module> myModules;

    JPackageArtifactTemplate(List<Module> modules) {
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
      final Module module;
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
        else {
          module = null;
        }
      }
      if (module == null) return null;
      final CompositePackagingElement<?> rootElement = JPackageArtifactType.this.createRootElement(module.getName());
      final CompositePackagingElement<?>
        subElement = PackagingElementFactory.getInstance().createArchive(FileUtil.sanitizeFileName(module.getName()) + ".jar");
      final PackagingElement<?> moduleOutputElement = PackagingElementFactory.getInstance().createModuleOutput(module);
      subElement.addFirstChild(moduleOutputElement);
      rootElement.addFirstChild(subElement);
      return new NewArtifactConfiguration(rootElement, module.getName(), JPackageArtifactType.this);
    }
  }
}
