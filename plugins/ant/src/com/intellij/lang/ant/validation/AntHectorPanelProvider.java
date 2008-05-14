package com.intellij.lang.ant.validation;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.editor.HectorComponentPanelsProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: May 12, 2008
 */
public class AntHectorPanelProvider implements HectorComponentPanelsProvider{
  public HectorComponentPanel createConfigurable(@NotNull final PsiFile file) {
    final AntFile antFile = AntSupport.getAntFile(file);
    if (antFile == null) {
      return null;
    }
    return new AntHectorConfigurable(antFile);
  }
}
