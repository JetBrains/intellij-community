// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.platform.debugger.impl.rpc.XDataBreakpointInfo;
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.breakpoints.XDataBreakpointAccessType;
import com.intellij.xdebugger.breakpoints.XDataBreakpointLifetime;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

@ApiStatus.Internal
public class XDataBreakpointTopPropertiesPanel extends XBreakpointPropertiesSubPanel {
  private final JBTextField myExpressionField = new JBTextField();
  private final ComboBox<String> mySizeCombo = new ComboBox<>();
  private final ComboBox<XDataBreakpointAccessType> myAccessTypeCombo =
    new ComboBox<>(new EnumComboBoxModel<>(XDataBreakpointAccessType.class));
  private final ComboBox<XDataBreakpointLifetime> myLifetimeCombo =
    new ComboBox<>(new EnumComboBoxModel<>(XDataBreakpointLifetime.class));
  private final JLabel myLifetimeLabel = new JLabel(XDebuggerBundle.message("xbreakpoints.data.lifetime.label"));
  private final JLabel myErrorLabel = new JLabel();
  private final JComponent myComponent;
  // False when the driver does not support lifetime; keeps the row hidden and stops saveProperties from overwriting null.
  private boolean myLifetimeSupported;
  private Disposable myDiagnosticsListenerDisposable;

  public XDataBreakpointTopPropertiesPanel() {
    myErrorLabel.setForeground(JBColor.RED);
    myErrorLabel.setVisible(false);

    // The expression can only be set when the data breakpoint is created (see CidrWatchpointPropertiesPanel's
    // editable=false branch in the monolith), so it is shown as a read-only edit field here, not a free label.
    myExpressionField.setEditable(false);

    // The size is fixed at creation time too (sizeUserEditable is always false in the properties/edit panel,
    // even in the monolith) - shown as an always-disabled combobox for visual parity with the Add dialog's,
    // sized via prototypeDisplayValue the same way CidrWatchpointPropertiesPanel.sizeComboBox is.
    mySizeCombo.setEnabled(false);
    mySizeCombo.setPrototypeDisplayValue(XDebuggerBundle.message("xbreakpoints.data.size.bytes", 88888888));

    myAccessTypeCombo.setRenderer(new SimpleListCellRenderer<>() {
      @Override
      public void customize(@NotNull JList<? extends XDataBreakpointAccessType> list, @Nullable XDataBreakpointAccessType value,
                             int index, boolean selected, boolean hasFocus) {
        setText(value == null ? "" : getAccessTypeText(value));
      }
    });
    myLifetimeCombo.setRenderer(new SimpleListCellRenderer<>() {
      @Override
      public void customize(@NotNull JList<? extends XDataBreakpointLifetime> list, @Nullable XDataBreakpointLifetime value,
                             int index, boolean selected, boolean hasFocus) {
        setText(value == null ? "" : getLifetimeText(value));
      }
    });

    JPanel sizeGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
    sizeGroup.add(new JLabel(XDebuggerBundle.message("xbreakpoints.data.size.label")));
    sizeGroup.add(mySizeCombo);

    JPanel expressionRow = new JPanel(new BorderLayout(JBUI.scale(12), 0));
    expressionRow.add(myExpressionField, BorderLayout.CENTER);
    expressionRow.add(sizeGroup, BorderLayout.EAST);

    myComponent = FormBuilder.createFormBuilder()
      .addLabeledComponent(XDebuggerBundle.message("xbreakpoints.data.expression.label"), expressionRow)
      .addLabeledComponent(XDebuggerBundle.message("xbreakpoints.data.accessType.label"), myAccessTypeCombo)
      .addLabeledComponent(myLifetimeLabel, myLifetimeCombo)
      .addComponent(myErrorLabel)
      .getPanel();
  }

  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void init(Project project, @NotNull XBreakpointProxy breakpoint) {
    super.init(project, breakpoint);
    myDiagnosticsListenerDisposable = Disposer.newDisposable("XDataBreakpointTopPropertiesPanel");
    myBreakpoint.subscribeOnDataBreakpointInfoChanges(myDiagnosticsListenerDisposable, info -> {
      updateDiagnostics(info);
      return Unit.INSTANCE;
    });
  }

  @Override
  public void dispose() {
    if (myDiagnosticsListenerDisposable != null) {
      Disposer.dispose(myDiagnosticsListenerDisposable);
      myDiagnosticsListenerDisposable = null;
    }
  }

  @Override
  void loadProperties() {
    XDataBreakpointInfo info = myBreakpoint.getDataBreakpointInfo();
    if (info == null) return;

    myExpressionField.setText(info.getExpressionPresentation());
    updateDiagnostics(info);

    XDataBreakpointAccessType accessType = info.getAccessType();
    if (accessType != null) {
      myAccessTypeCombo.setSelectedItem(accessType);
    }

    XDataBreakpointLifetime lifetime = info.getLifetime();
    myLifetimeSupported = lifetime != null;
    myLifetimeLabel.setVisible(myLifetimeSupported);
    myLifetimeCombo.setVisible(myLifetimeSupported);
    if (lifetime != null) {
      myLifetimeCombo.setSelectedItem(lifetime);
    }
  }

  // Refreshes only the read-only diagnostic fields (size, registration error). Never touches
  // myAccessTypeCombo/myLifetimeCombo/myLifetimeLabel - those are load-once and save-on-teardown.
  private void updateDiagnostics(@NotNull XDataBreakpointInfo info) {
    Integer sizeBytes = info.getSizeBytes();
    String sizeText = sizeBytes == null
                       ? XDebuggerBundle.message("xbreakpoints.data.size.unknown")
                       : XDebuggerBundle.message("xbreakpoints.data.size.bytes", sizeBytes);
    mySizeCombo.removeAllItems();
    mySizeCombo.addItem(sizeText);
    mySizeCombo.setSelectedItem(sizeText);

    String error = info.getRegistrationError();
    myErrorLabel.setText(error);
    myErrorLabel.setVisible(error != null);
  }

  @Override
  void saveProperties() {
    myBreakpoint.setDataBreakpointAccessType((XDataBreakpointAccessType)myAccessTypeCombo.getSelectedItem());
    if (myLifetimeSupported) {
      myBreakpoint.setDataBreakpointLifetime((XDataBreakpointLifetime)myLifetimeCombo.getSelectedItem());
    }
  }

  private static @NotNull String getAccessTypeText(@NotNull XDataBreakpointAccessType accessType) {
    return switch (accessType) {
      case READ -> XDebuggerBundle.message("xbreakpoints.data.accessType.read");
      case WRITE -> XDebuggerBundle.message("xbreakpoints.data.accessType.write");
      case ANY -> XDebuggerBundle.message("xbreakpoints.data.accessType.any");
    };
  }

  private static @NotNull String getLifetimeText(@NotNull XDataBreakpointLifetime lifetime) {
    return switch (lifetime) {
      case STACK_FRAME -> XDebuggerBundle.message("xbreakpoints.data.lifetime.stackFrame");
      case PERSISTENT -> XDebuggerBundle.message("xbreakpoints.data.lifetime.persistent");
    };
  }
}
