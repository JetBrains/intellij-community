package org.jetbrains.android.compiler.artifact;

import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.FacetBasedPackagingSourceItemsProvider;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.ModuleSourceItemGroup;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSourceItemsProvider extends FacetBasedPackagingSourceItemsProvider<AndroidFacet, AndroidFinalPackageElement> {
  public AndroidSourceItemsProvider() {
    super(AndroidFacet.ID, AndroidFinalPackageElementType.getInstance());
  }

  @Override
  protected AndroidFinalPackagePresentation createPresentation(AndroidFacet facet) {
    return new AndroidFinalPackagePresentation(FacetPointersManager.getInstance(facet.getModule().getProject()).create(facet));
  }

  @Override
  protected AndroidFinalPackageElement createElement(ArtifactEditorContext context, AndroidFacet facet) {
    return new AndroidFinalPackageElement(context.getProject(), facet);
  }

  @NotNull
  @Override
  public Collection<? extends PackagingSourceItem> getSourceItems(@NotNull ArtifactEditorContext editorContext,
                                                                  @NotNull Artifact artifact,
                                                                  @Nullable PackagingSourceItem parent) {

    if (parent instanceof ModuleSourceItemGroup && !AndroidArtifactUtil.containsAndroidPackage(editorContext, artifact)) {
      final Module module = ((ModuleSourceItemGroup)parent).getModule();
      final Set<AndroidFacet> facets =
        new HashSet<AndroidFacet>(editorContext.getFacetsProvider().getFacetsByType(module, AndroidFacet.ID));
      if (facets.size() > 0) {
        return Collections.singletonList(new FacetBasedSourceItem<AndroidFacet>(this, facets.iterator().next()));
      }
    }
    return Collections.emptyList();
  }

  @Override
  protected AndroidFacet getFacet(AndroidFinalPackageElement element) {
    return element.getFacet();
  }
}
