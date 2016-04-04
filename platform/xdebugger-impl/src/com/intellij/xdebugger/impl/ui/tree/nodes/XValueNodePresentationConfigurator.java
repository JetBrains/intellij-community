/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullFunction;
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
    public void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull String separator,
                                @NonNls @Nullable String value, boolean hasChildren) {
      XValueNodePresentationConfigurator.setPresentation(icon, type, separator, value, hasChildren, this);
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
                                     @Nullable NotNullFunction<String, String> valuePresenter,
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
      Runnable updater = new Runnable() {
        @Override
        public void run() {
          node.applyPresentation(icon, presentation, hasChildren);
        }
      };
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
    private final NotNullFunction<String, String> valuePresenter;

    public XValuePresentationAdapter(String value, String type, NotNullFunction<String, String> valuePresenter) {
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
      renderer.renderValue(valuePresenter.fun(myValue));
    }
  }
}