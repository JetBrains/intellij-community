// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import java.util.function.Function;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class XValueNodePresentationConfigurator {
  public interface ConfigurableXValueNode {
    void applyPresentation(@Nullable Icon icon,
                           @NotNull XValuePresentation valuePresenter,
                           boolean hasChildren);
  }

  public static abstract class ConfigurableXValueNodeImpl implements ConfigurableXValueNode, XValueNode {
    @Override
    public void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull String value, boolean hasChildren) {
      XValueNodePresentationConfigurator.setPresentation(icon, type, value, hasChildren, this);
    }

    @Override
    public void setPresentation(@Nullable Icon icon, @NotNull XValuePresentation presentation, boolean hasChildren) {
      XValueNodePresentationConfigurator.setPresentation(icon, presentation, hasChildren, this);
    }
  }

  public static void setPresentation(@Nullable Icon icon, @NotNull XValuePresentation presentation, boolean hasChildren,
                                     ConfigurableXValueNode node) {
    doSetPresentation(icon, presentation, hasChildren, node);
  }

  public static void setPresentation(@Nullable Icon icon,
                                     @NonNls @Nullable String type,
                                     @NonNls @NotNull String value,
                                     boolean hasChildren,
                                     ConfigurableXValueNode node) {
    doSetPresentation(icon, new XRegularValuePresentation(value, type), hasChildren, node);
  }

  public static void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull final String separator,
                                     @NonNls @Nullable String value, boolean hasChildren, ConfigurableXValueNode node) {
    doSetPresentation(icon, new XRegularValuePresentation(StringUtil.notNullize(value), type, separator), hasChildren, node);
  }

  public static void setPresentation(@Nullable Icon icon,
                                     @NonNls @Nullable String type,
                                     @NonNls @NotNull String value,
                                     @Nullable Function<? super String, @NotNull String> valuePresenter,
                                     boolean hasChildren, ConfigurableXValueNode node) {
    doSetPresentation(icon,
                      valuePresenter == null ? new XRegularValuePresentation(value, type) : new XValuePresentationAdapter(value, type, valuePresenter),
                      hasChildren, node);
  }

  private static void doSetPresentation(@Nullable final Icon icon,
                                        @NotNull final XValuePresentation presentation,
                                        final boolean hasChildren,
                                        final ConfigurableXValueNode node) {
    if (DebuggerUIUtil.isObsolete(node)) {
      return;
    }

    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      node.applyPresentation(icon, presentation, hasChildren);
    }
    else {
      Runnable updater = () -> node.applyPresentation(icon, presentation, hasChildren);
      if (node instanceof XDebuggerTreeNode) {
        ((XDebuggerTreeNode)node).invokeNodeUpdate(updater);
      }
      else {
        application.invokeLater(updater);
      }
    }
  }

  private static final class XValuePresentationAdapter extends XValuePresentation {
    private final String myValue;
    private final String myType;
    private final Function<? super String, @NotNull String> valuePresenter;

    XValuePresentationAdapter(String value, String type, Function<? super String, @NotNull String> valuePresenter) {
      myValue = value;
      myType = type;
      this.valuePresenter = valuePresenter;
    }

    @Nullable
    @Override
    public String getType() {
      return myType;
    }

    @Override
    public void renderValue(@NotNull XValueTextRenderer renderer) {
      renderer.renderValue(valuePresenter.apply(myValue));
    }
  }
}