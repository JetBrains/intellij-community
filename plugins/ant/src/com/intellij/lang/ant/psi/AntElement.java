package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.PsiAntElement;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntElement extends PsiAntElement {
  AntElement[] EMPTY_ARRAY = new AntElement[0];

  @NotNull
  XmlElement getSourceElement();

  @Nullable
  AntElement getAntParent();

  AntFile getAntFile();

  AntProject getAntProject();

  void clearCaches();

  @Nullable
  AntProperty getProperty(final String name);

  void setProperty(final String name, final AntProperty element);

  @NotNull
  AntProperty[] getProperties();

  /**
   * Does the same as findElementAt() but without rebuilding cleared PSI.
   *
   * @param offset
   * @return
   */
  AntElement lightFindElementAt(int offset);
}
