package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.config.impl.AntClassLoader;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntFile extends PsiFile, AntElement {

  AntFile[] NO_FILES = new AntFile[0];

  @NotNull
  XmlFile getSourceElement();

  AntClassLoader getClassLoader();

  @NotNull
  AntTypeDefinition[] getBaseTypeDefinitions();

  @Nullable
  AntTypeDefinition getBaseTypeDefinition(final String taskClassName);

  @NotNull
  AntTypeDefinition getTargetDefinition();

  void registerCustomType(final AntTypeDefinition def);

  void unregisterCustomType(final AntTypeDefinition def);

  VirtualFile getVirtualFile();

  void setProperty(@NotNull final String name, @NotNull final String value);

  @Nullable
  VirtualFile getContainingPath();
}
