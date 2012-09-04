package org.jetbrains.android.compiler.artifact;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFinalPackageElementType extends PackagingElementType<AndroidFinalPackageElement> {
  @NonNls public static final String TYPE_ID = "android-final-package";

  protected AndroidFinalPackageElementType() {
    super(TYPE_ID, "Android Final Package");
  }

  public static AndroidFinalPackageElementType getInstance() {
    return getInstance(AndroidFinalPackageElementType.class);
  }

  @Override
  public Icon getCreateElementIcon() {
    return AndroidIcons.Android;
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return getAndroidApplicationFacets(context, context.getModulesProvider().getModules()).size() > 0 &&
           !AndroidArtifactUtil.containsAndroidPackage(context, artifact);
  }

  @NotNull
  private static List<AndroidFacet> getAndroidApplicationFacets(@NotNull ArtifactEditorContext context, @NotNull Module[] modules) {
    final List<AndroidFacet> result = new ArrayList<AndroidFacet>();
    for (Module module : modules) {
      for (AndroidFacet facet : context.getFacetsProvider().getFacetsByType(module, AndroidFacet.ID)) {
        if (!facet.getConfiguration().LIBRARY_PROJECT) {
          result.add(facet);
        }
      }
    }
    return result;
  }

  @NotNull
  @Override
  public List<? extends PackagingElement<?>> chooseAndCreate(@NotNull ArtifactEditorContext context,
                                                             @NotNull Artifact artifact,
                                                             @NotNull CompositePackagingElement<?> parent) {
    final List<AndroidFacet> facets = getAndroidApplicationFacets(context, context.getModulesProvider().getModules());

    final AndroidFacet facet = AndroidArtifactUtil.chooseAndroidApplicationModule(context.getProject(), facets);
    if (facet == null) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new AndroidFinalPackageElement(context.getProject(), facet));
  }

  @NotNull
  @Override
  public AndroidFinalPackageElement createEmpty(@NotNull Project project) {
    return new AndroidFinalPackageElement(project, null);
  }
}
