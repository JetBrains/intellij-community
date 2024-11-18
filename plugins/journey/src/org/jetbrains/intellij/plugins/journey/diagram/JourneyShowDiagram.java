package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.uml.core.actions.ShowDiagram;

import java.awt.*;
import java.util.Collections;

public class JourneyShowDiagram extends ShowDiagram {
  public void showDiagram(PsiElement file) {
    DiagramSeed seed = createSeed(file.getProject(), new JourneyDiagramProvider(), new JourneyNodeIdentity(file), Collections.emptyList());
    showUnderProgress(seed, new RelativePoint(new Point()));
  }

  @Override
  protected boolean isPopup() {
    return false;
  }

}
