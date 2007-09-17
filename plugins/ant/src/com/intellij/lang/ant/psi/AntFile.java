package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface AntFile extends PsiFile, AntElement, PsiNamedElement, ModificationTracker {

  AntFile[] NO_FILES = new AntFile[0];

  @NotNull
  XmlFile getSourceElement();

  ClassLoader getClassLoader();

  @Nullable
  AntInstallation getAntInstallation(); 
  
  @NotNull
  AntTypeDefinition[] getBaseTypeDefinitions();

  @Nullable
  AntTypeDefinition getBaseTypeDefinition(final String taskClassName);

  @Nullable /* will return null in case ant installation was not properly configured*/
  AntTypeDefinition getTargetDefinition();

  void registerCustomType(final AntTypeDefinition def);

  void unregisterCustomType(final AntTypeDefinition def);

  VirtualFile getVirtualFile();

  void clearExternalProperties();
  
  void setExternalProperty(@NotNull final String name, @NotNull final String value);

  @Nullable
  VirtualFile getContainingPath();

  void clearCachesWithTypeDefinitions();
  
  void addEnvironmentPropertyPrefix(@NotNull final String envPrefix);

  boolean isEnvironmentProperty(@NotNull final String propName);

  List<String> getEnvironmentPrefixes();

  @Nullable
  AntProperty getProperty(@NonNls final String name);

  void setProperty(final String name, final AntProperty element);

  @NotNull
  AntProperty[] getProperties();

  void invalidateProperties();
}
