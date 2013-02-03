package com.intellij.ui;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

public class RightMarginEditorCustomization extends EditorCustomization {

  @Override
  protected Class<? extends EditorFeature> getFeatureClass() {
    return RightMarginEditorFeature.class;
  }

  @Override
  protected void customize(@NotNull EditorEx editor, @NotNull EditorFeature feature) {
    if (!(feature instanceof RightMarginEditorFeature)) {
      return;
    }

    RightMarginEditorFeature rightMarginEditorFeature = (RightMarginEditorFeature)feature;

    if (rightMarginEditorFeature.isEnabled()) {
      editor.getSettings().setRightMarginShown(true);
      editor.getSettings().setRightMargin(rightMarginEditorFeature.getRightMarginColumns());
      // ensure we've got a monospace font by loading up the global editor scheme
      editor.setColorsScheme(EditorColorsManager.getInstance().getGlobalScheme());
    } else {
      editor.getSettings().setRightMarginShown(false);
    }
  }
}
