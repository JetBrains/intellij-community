package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntFile extends PsiFile, AntElement, ModificationTracker {

  AntFile[] NO_FILES = new AntFile[0];

  @NotNull
  XmlFile getSourceElement();

  ClassLoader getClassLoader();

  @NotNull
  AntTypeDefinition[] getBaseTypeDefinitions();

  @Nullable
  AntTypeDefinition getBaseTypeDefinition(final String taskClassName);

  @Nullable /* will return null in case ant installation was not properly configured*/
  AntTypeDefinition getTargetDefinition();

  void registerCustomType(final AntTypeDefinition def);

  void unregisterCustomType(final AntTypeDefinition def);

  VirtualFile getVirtualFile();

  void setProperty(@NotNull final String name, @NotNull final String value);

  @Nullable
  VirtualFile getContainingPath();

  void clearCachesWithTypeDefinitions();
}
