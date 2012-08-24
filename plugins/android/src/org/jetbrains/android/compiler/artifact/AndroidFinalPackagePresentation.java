package org.jetbrains.android.compiler.artifact;

import com.intellij.facet.pointers.FacetPointer;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.SourceItemWeights;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidFinalPackagePresentation extends SourceItemPresentation {
  private final FacetPointer<AndroidFacet> myFacetPointer;

  public AndroidFinalPackagePresentation(@Nullable FacetPointer<AndroidFacet> facetPointer) {
    myFacetPointer = facetPointer;
  }

  @Override
  public String getPresentableName() {
    final String moduleName = myFacetPointer != null ? myFacetPointer.getModuleName() : "<unknown>";
    return "'" + moduleName + "' Android final package";
  }

  @Override
  public void render(@NotNull PresentationData presentationData,
                     SimpleTextAttributes mainAttributes,
                     SimpleTextAttributes commentAttributes) {
    presentationData.setIcon(AndroidFacet.getFacetType().getIcon());
    presentationData.addText(getPresentableName(), mainAttributes);
  }

  @Override
  public int getWeight() {
    return SourceItemWeights.LIBRARY_WEIGHT - 5;
  }
}
