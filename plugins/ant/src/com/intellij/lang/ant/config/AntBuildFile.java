package com.intellij.lang.ant.config;

import com.intellij.lang.ant.psi.AntFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.config.AbstractProperty;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

public interface AntBuildFile {

  @Nullable
  String getPresentableName();

  AntBuildModel getModel();

  @Nullable
  AntBuildModel getModelIfRegistered();

  AbstractProperty.AbstractPropertyContainer getAllOptions();

  AntFile getAntFile();

  Project getProject();

  VirtualFile getVirtualFile();

  String getPresentableUrl();

  boolean shouldExpand();

  boolean isTargetVisible(final AntBuildTarget target);

  void updateProperties();

  void updateConfig();

  void setTreeView(final boolean value);

  void setVerboseMode(final boolean value);

  boolean isViewClosedWhenNoErrors();

  boolean isRunInBackground();

  void readWorkspaceProperties(final Element element) throws InvalidDataException;

  void writeWorkspaceProperties(final Element element) throws WriteExternalException;

  void readProperties(final Element element) throws InvalidDataException;

  void writeProperties(final Element element) throws WriteExternalException;
}
