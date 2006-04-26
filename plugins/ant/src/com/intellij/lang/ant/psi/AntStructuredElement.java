package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntStructuredElement extends AntElement, PsiNamedElement {

  @NotNull
  XmlTag getSourceElement();

  @Nullable
  AntTypeDefinition getTypeDefinition();

  void registerCustomType(final AntTypeDefinition def);

  @Nullable
  String getId();

  void registerRefId(final String id, AntStructuredElement element);

  @Nullable
  AntStructuredElement getElementByRefId(final String refid);

  @NotNull
  String[] getRefIds();
}
