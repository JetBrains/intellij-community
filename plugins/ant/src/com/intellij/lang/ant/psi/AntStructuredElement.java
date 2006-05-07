package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntStructuredElement extends AntElement {

  @NotNull
  XmlTag getSourceElement();

  @Nullable
  AntTypeDefinition getTypeDefinition();

  void registerRefId(final String id, AntElement element);

  @Nullable
  AntElement getElementByRefId(final String refid);

  @NotNull
  String[] getRefIds();
}
