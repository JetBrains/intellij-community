// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.processing.HighlightMode;
import com.intellij.openapi.editor.markup.MarkupEditorFilter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.actions.migrate.MigrateDiffTool;
import com.intellij.util.config.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@State(
  name = "DiffManager",
  storages = {
    @Storage("diff.xml"),
    @Storage(value = "other.xml", deprecated = true)
  }
)
@Deprecated
public class DiffManagerImpl extends DiffManager implements PersistentStateComponent<Element> {
  public static final int FULL_DIFF_DIVIDER_POLYGONS_OFFSET = 3;
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
  public static final DiffTool INTERNAL_DIFF = new FrameDiffTool();

  private static final MarkupEditorFilter DIFF_EDITOR_FILTER = editor -> DiffUtil.isDiffEditor(editor);

  private ComparisonPolicy myComparisonPolicy = ComparisonPolicy.DEFAULT;
  private HighlightMode myHighlightMode = HighlightMode.BY_WORD;

  @NonNls public static final String COMPARISON_POLICY_ATTR_NAME = "COMPARISON_POLICY";
  @NonNls public static final String HIGHLIGHT_MODE_ATTR_NAME = "HIGHLIGHT_MODE";

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

  @Nullable
  @Override
  public Element getState() {
    Element state = new Element("state");
    myProperties.writeExternal(state);
    if (myComparisonPolicy != ComparisonPolicy.DEFAULT) {
      state.setAttribute(COMPARISON_POLICY_ATTR_NAME, myComparisonPolicy.getName());
    }
    if (myHighlightMode != HighlightMode.BY_WORD) {
      state.setAttribute(HIGHLIGHT_MODE_ATTR_NAME, myHighlightMode.name());
    }
    return state;
  }

  @Override
  public void loadState(@NotNull Element state) {
    myProperties.readExternal(state);

    String policyName = state.getAttributeValue(COMPARISON_POLICY_ATTR_NAME);
    if (policyName != null) {
      for (ComparisonPolicy policy : ComparisonPolicy.getAllInstances()) {
        if (policy.getName().equals(policyName)) {
          myComparisonPolicy = policy;
          break;
        }
      }
    }

    String modeName = state.getAttributeValue(HIGHLIGHT_MODE_ATTR_NAME);
    if (modeName != null) {
      try {
        myHighlightMode = HighlightMode.valueOf(modeName);
      }
      catch (IllegalArgumentException ignore) {
      }
    }
  }

  public AbstractProperty.AbstractPropertyContainer getProperties() {
    return myProperties;
  }

  static DiffPanel createDiffPanel(DiffRequest data, Window window, @NotNull Disposable parentDisposable, DiffTool tool) {
    DiffPanel diffPanel = null;
    try {
      diffPanel = new DiffPanelImpl(window, data.getProject(), true, true, FULL_DIFF_DIVIDER_POLYGONS_OFFSET, tool);
      Disposer.register(parentDisposable, diffPanel);
      int contentCount = data.getContents().length;
      LOG.assertTrue(contentCount == 2, String.valueOf(contentCount));
      LOG.assertTrue(data.getContentTitles().length == contentCount);
      diffPanel.setDiffRequest(data);
      return diffPanel;
    }
    catch (RuntimeException e) {
      if (diffPanel != null) {
        Disposer.dispose(diffPanel);
      }
      throw e;
    }
  }

  @NotNull
  public ComparisonPolicy getComparisonPolicy() {
    return myComparisonPolicy;
  }

  public void setComparisonPolicy(@NotNull ComparisonPolicy value) {
    myComparisonPolicy = value;
  }

  @NotNull
  public HighlightMode getHighlightMode() {
    return myHighlightMode;
  }

  public void setHighlightMode(@NotNull HighlightMode highlightMode) {
    myHighlightMode = highlightMode;
  }
}
