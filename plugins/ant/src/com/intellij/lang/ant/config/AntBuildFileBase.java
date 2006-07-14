package com.intellij.lang.ant.config;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.config.AbstractProperty;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

public interface AntBuildFileBase extends AntBuildFile {

  AntBuildModelBase getModel();

  @Nullable
  AntBuildModelBase getModelIfRegistered();

  AbstractProperty.AbstractPropertyContainer getAllOptions();

  boolean shouldExpand();

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
