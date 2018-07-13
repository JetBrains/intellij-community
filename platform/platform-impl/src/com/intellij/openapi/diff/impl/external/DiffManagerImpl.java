// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.markup.MarkupEditorFilter;
import com.intellij.openapi.vcs.changes.actions.migrate.MigrateDiffTool;
import com.intellij.util.config.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

@Deprecated
public class DiffManagerImpl extends DiffManager {
  private static final Logger LOG = Logger.getInstance(DiffManagerImpl.class);

  private static final Externalizer<String> TOOL_PATH_UPDATE = new Externalizer<String>() {
    @NonNls private static final String NEW_VALUE = "newValue";

    @Override
    public String readValue(Element dataElement) {
      String path = dataElement.getAttributeValue(NEW_VALUE);
      if (path != null) {
        return path;
      }

      String prevValue = dataElement.getAttributeValue(VALUE_ATTRIBUTE);
      return prevValue != null ? prevValue.trim() : null;
    }

    @Override
    public void writeValue(Element dataElement, String path) {
      dataElement.setAttribute(VALUE_ATTRIBUTE, path);
      dataElement.setAttribute(NEW_VALUE, path);
    }
  };

  public static final StringProperty FOLDERS_TOOL = new StringProperty("foldersTool", "");
  public static final StringProperty FILES_TOOL = new StringProperty("filesTool", "");
  public static final StringProperty MERGE_TOOL = new StringProperty("mergeTool", "");
  public static final StringProperty MERGE_TOOL_PARAMETERS = new StringProperty("mergeToolParameters", "");
  public static final BooleanProperty ENABLE_FOLDERS = new BooleanProperty("enableFolders", false);
  public static final BooleanProperty ENABLE_FILES = new BooleanProperty("enableFiles", false);
  public static final BooleanProperty ENABLE_MERGE = new BooleanProperty("enableMerge", false);

  private final ExternalizablePropertyContainer myProperties;

  private static final MarkupEditorFilter DIFF_EDITOR_FILTER = editor -> DiffUtil.isDiffEditor(editor);

  public DiffManagerImpl() {
    myProperties = new ExternalizablePropertyContainer();
    myProperties.registerProperty(ENABLE_FOLDERS);
    myProperties.registerProperty(FOLDERS_TOOL, TOOL_PATH_UPDATE);
    myProperties.registerProperty(ENABLE_FILES);
    myProperties.registerProperty(FILES_TOOL, TOOL_PATH_UPDATE);
    myProperties.registerProperty(ENABLE_MERGE);
    myProperties.registerProperty(MERGE_TOOL, TOOL_PATH_UPDATE);
    myProperties.registerProperty(MERGE_TOOL_PARAMETERS);
  }

  @Override
  public DiffTool getIdeaDiffTool() {
    return MigrateDiffTool.INSTANCE;
  }

  @Override
  public DiffTool getDiffTool() {
    return MigrateDiffTool.INSTANCE;
  }

  @Override
  public MarkupEditorFilter getDiffEditorFilter() {
    return DIFF_EDITOR_FILTER;
  }

  public static DiffManagerImpl getInstanceEx() {
    return (DiffManagerImpl)DiffManager.getInstance();
  }

  public AbstractProperty.AbstractPropertyContainer getProperties() {
    return myProperties;
  }
}
