package org.jetbrains.android.compiler.artifact;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.LibrarySourceItem;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.ModuleOutputSourceItem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactTemplate;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidApplicationArtifactType extends ArtifactType {
  public AndroidApplicationArtifactType() {
    super("apk", "Android Application");
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return PlainArtifactType.ARTIFACT_ICON;
  }

  @Override
  public String getDefaultPathFor(@NotNull PackagingElementOutputKind kind) {
    return "/";
  }

  @Override
  public boolean isSuitableItem(@NotNull PackagingSourceItem item) {
    return !(item instanceof ModuleOutputSourceItem || item instanceof LibrarySourceItem);
  }

  @NotNull
  @Override
  public CompositePackagingElement<?> createRootElement(@NotNull String artifactName) {
    return PackagingElementFactory.getInstance().createArchive(ArtifactUtil.suggestArtifactFileName(artifactName) + ".apk");
  }


  @NotNull
  @Override
  public List<? extends ArtifactTemplate> getNewArtifactTemplates(@NotNull PackagingElementResolvingContext context) {
    return Collections.singletonList(new MyTemplate(context));
  }

  private class MyTemplate extends ArtifactTemplate {
    protected PackagingElementResolvingContext myContext;

    public MyTemplate(@NotNull PackagingElementResolvingContext context) {
      myContext = context;
    }

    @Override
    public String getPresentableName() {
      return "From module...";
    }

    @Override
    public NewArtifactConfiguration createArtifact() {
      final List<Module> modules = new ArrayList<Module>();

      for (Module module : myContext.getModulesProvider().getModules()) {
        final AndroidFacet facet = AndroidFacet.getInstance(module);

        if (facet != null && !facet.getConfiguration().LIBRARY_PROJECT) {
          modules.add(module);
        }
      }

      final AndroidFacet facet = AndroidArtifactUtil.chooseAndroidApplicationModule(myContext.getProject(), modules);
      if (facet == null) {
        return null;
      }

      final CompositePackagingElement<?> rootElement =
        AndroidApplicationArtifactType.this.createRootElement(facet.getModule().getName());
      rootElement.addFirstChild(new AndroidFinalPackageElement(myContext.getProject(), facet));
      return new NewArtifactConfiguration(rootElement, facet.getModule().getName(), AndroidApplicationArtifactType.this);
    }

    @Override
    public void setUpArtifact(@NotNull Artifact artifact, @NotNull NewArtifactConfiguration configuration) {
      final AndroidFacet facet = AndroidArtifactUtil.getPackagedFacet(myContext.getProject(), artifact);

      if (facet != null) {
        final ArtifactProperties<?> properties = artifact.getProperties(AndroidArtifactPropertiesProvider.getInstance());

        if (properties instanceof AndroidApplicationArtifactProperties) {
          final AndroidApplicationArtifactProperties p = (AndroidApplicationArtifactProperties)properties;

          final VirtualFile proguardCfgFile = AndroidRootUtil.getProguardCfgFile(facet);
          if (proguardCfgFile != null) {
            p.setProGuardCfgFileUrl(proguardCfgFile.getUrl());
          }

          p.setIncludeSystemProGuardCfgFile(facet.getConfiguration().isIncludeSystemProguardCfgPath());
        }
      }
    }
  }
}
