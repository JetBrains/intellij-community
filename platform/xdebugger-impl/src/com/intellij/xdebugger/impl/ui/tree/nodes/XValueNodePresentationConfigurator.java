package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ObjectUtils;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePresenter;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class XValueNodePresentationConfigurator {
  private static final XValuePresenter DEFAULT_VALUE_PRESENTER = new XVariableValuePresenter(XDebuggerUIConstants.EQ_TEXT);

  public interface ConfigurableXValueNode {
    void applyPresentation(@Nullable Icon icon,
                           @Nullable String type,
                           @Nullable String value,
                           @NotNull XValuePresenter valuePresenter,
                           boolean hasChildren,
                           boolean expand);
  }

  public static abstract class ConfigurableXValueNodeImpl implements ConfigurableXValueNode, XValueNode {
    @Override
    public void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @Nullable String value, boolean hasChildren) {
      XValueNodePresentationConfigurator.setPresentation(icon, type, value, hasChildren, this);
    }

    @Override
    public void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull String separator,
                                @NonNls @Nullable String value, boolean hasChildren) {
      XValueNodePresentationConfigurator.setPresentation(icon, type, separator, value, hasChildren, this);
    }

    @Override
    public void setPresentation(@Nullable Icon icon,
                                @NonNls @Nullable String type,
                                @NonNls @NotNull String value,
                                @Nullable NotNullFunction<String, String> valuePresenter,
                                boolean hasChildren) {
      XValueNodePresentationConfigurator.setPresentation(icon, type, value, valuePresenter, hasChildren, this);
    }

    @Override
    public void setPresentation(@Nullable Icon icon,
                                @NonNls @Nullable String type,
                                @NonNls @NotNull String value,
                                @Nullable XValuePresenter valuePresenter,
                                boolean hasChildren) {
      XValueNodePresentationConfigurator.setPresentation(icon, type, value, valuePresenter, hasChildren, this);
    }

    @Override
    public void setPresentation(@Nullable Icon icon,
                                @NonNls @Nullable String type,
                                @NonNls @NotNull String separator,
                                @NonNls @NotNull String value,
                                final @Nullable NotNullFunction<String, String> valuePresenter,
                                boolean hasChildren) {
      XValueNodePresentationConfigurator.setPresentation(icon, type, separator, valuePresenter, hasChildren, this);
    }

    @Override
    public void setGroupingPresentation(@Nullable Icon icon,
                                        @NonNls @Nullable String value,
                                        @Nullable XValuePresenter valuePresenter,
                                        boolean expand) {
      XValueNodePresentationConfigurator.setGroupingPresentation(icon, value, valuePresenter, expand, this);
    }

    @Override
    public void setPresentation(@Nullable Icon icon,
                                @NonNls @Nullable String value,
                                @Nullable XValuePresenter valuePresenter,
                                boolean hasChildren) {
      XValueNodePresentationConfigurator.setPresentation(icon, value, valuePresenter, hasChildren, this);
    }
  }

  public static void setPresentation(@Nullable Icon icon,
                                     @NonNls @Nullable String type,
                                     @NonNls @Nullable String value,
                                     boolean hasChildren,
                                     ConfigurableXValueNode node) {
    doSetPresentation(icon, type, value, DEFAULT_VALUE_PRESENTER, hasChildren, false, node);
  }

  public static void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull String separator,
                                     @NonNls @Nullable String value, boolean hasChildren, ConfigurableXValueNode node) {
    doSetPresentation(icon, type, value, createPresenter(separator), hasChildren, false, node);
  }

  private static XValuePresenter createPresenter(@NotNull String separator) {
    return separator.equals(XDebuggerUIConstants.EQ_TEXT) ? DEFAULT_VALUE_PRESENTER : new XVariableValuePresenter(separator);
  }

  public static void setPresentation(@Nullable Icon icon,
                                     @NonNls @Nullable String type,
                                     @NonNls @NotNull String value,
                                     @Nullable NotNullFunction<String, String> valuePresenter,
                                     boolean hasChildren, ConfigurableXValueNode node) {
    doSetPresentation(icon, type, value,
                      valuePresenter == null ? DEFAULT_VALUE_PRESENTER : new XValuePresenterAdapter(valuePresenter),
                      hasChildren, false, node);
  }

  public static void setPresentation(@Nullable Icon icon,
                                     @NonNls @Nullable String type,
                                     @NonNls @NotNull String value,
                                     @Nullable XValuePresenter valuePresenter,
                                     boolean hasChildren, ConfigurableXValueNode node) {
    doSetPresentation(icon, type, value, valuePresenter, hasChildren, false, node);
  }

  public static void setGroupingPresentation(@Nullable Icon icon,
                                             @NonNls @Nullable String value,
                                             @Nullable XValuePresenter valuePresenter,
                                             boolean expand,
                                             ConfigurableXValueNode node) {
    doSetPresentation(icon, null, value, valuePresenter, true, expand, node);
  }

  public static void setPresentation(@Nullable Icon icon,
                                     @NonNls @Nullable String value,
                                     @Nullable XValuePresenter valuePresenter,
                                     boolean hasChildren,
                                     ConfigurableXValueNode node) {
    doSetPresentation(icon, null, value, valuePresenter, hasChildren, false, node);
  }

  private static void doSetPresentation(@Nullable final Icon icon,
                                        @NonNls @Nullable final String type,
                                        @NonNls @Nullable final String value,
                                        @Nullable XValuePresenter valuePresenter,
                                        final boolean hasChildren,
                                        final boolean expand,
                                        final ConfigurableXValueNode node) {
    Application application = ApplicationManager.getApplication();
    final XValuePresenter finalValuePresenter = ObjectUtils.notNull(valuePresenter, DEFAULT_VALUE_PRESENTER);
    if (application.isDispatchThread()) {
      node.applyPresentation(icon, type, value, finalValuePresenter, hasChildren, expand);
    }
    else {
      application.invokeLater(new Runnable() {
        @Override
        public void run() {
          node.applyPresentation(icon, type, value, finalValuePresenter, hasChildren, expand);
        }
      });
    }
  }

  private static final class XValuePresenterAdapter extends XValuePresenter {
    private final NotNullFunction<String, String> valuePresenter;

    public XValuePresenterAdapter(NotNullFunction<String, String> valuePresenter) {
      this.valuePresenter = valuePresenter;
    }

    @Override
    public void append(@NotNull String value, @NotNull ColoredTextContainer text, boolean changed) {
      text.append(valuePresenter.fun(value), changed ? XDebuggerUIConstants.CHANGED_VALUE_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}