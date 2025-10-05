// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.messages;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.messages.MessageDialog;
import com.intellij.openapi.ui.messages.MessagesService;
import com.intellij.openapi.ui.messages.TwoStepConfirmationDialog;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Function;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.SwingUndoUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import static com.intellij.credentialStore.CredentialPromptDialog.getTrimmedChars;
import static com.intellij.openapi.ui.Messages.*;

public class MessagesServiceImpl implements MessagesService {
  @Override
  public int showMessageDialog(@Nullable Project project,
                               @Nullable Component parentComponent,
                               String message,
                               @Nls(capitalization = Nls.Capitalization.Title) String title,
                               String @NotNull [] options,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               @Nullable Icon icon,
                               @Nullable DoNotAskOption doNotAskOption,
                               boolean alwaysUseIdeaUI,
                               @Nullable String helpId,
                               @Nullable String invocationPlace,
                               ExitActionType @NotNull [] exitActionTypes) {
    if (isApplicationInUnitTestOrHeadless()) {
      return TestDialogManager.getTestImplementation().show(message, doNotAskOption);
    }

    AlertMessagesManager alertMessagesManager = AlertMessagesManager.getInstanceIfPossible();
    if (alertMessagesManager != null) {
      return alertMessagesManager.showMessageDialog(project, parentComponent, message, title, options, defaultOptionIndex,
                                                    focusedOptionIndex, icon, doNotAskOption, helpId, invocationPlace, exitActionTypes);
    }

    MessageDialog dialog = new MessageDialog(project, parentComponent, message, title, options, defaultOptionIndex, focusedOptionIndex,
                                             icon, doNotAskOption, false, helpId, invocationPlace, exitActionTypes);
    dialog.show();
    return dialog.getExitCode();
  }

  @Override
  @RequiresEdt
  public int showMoreInfoMessageDialog(Project project,
                                       String message,
                                       String title,
                                       @NlsContexts.DetailedDescription String moreInfo,
                                       String[] options,
                                       int defaultOptionIndex,
                                       int focusedOptionIndex,
                                       Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return TestDialogManager.getTestImplementation().show(message);
    }

    if (moreInfo == null) {
      AlertMessagesManager alertMessagesManager = AlertMessagesManager.getInstanceIfPossible();
      if (alertMessagesManager != null) {
        return alertMessagesManager.showMessageDialog(project, null, message, title, options, defaultOptionIndex, focusedOptionIndex, icon,
                                                      null, null, null, new ExitActionType[]{});
      }
    }

    MessageDialog dialog =
      new MoreInfoMessageDialog(project, message, title, moreInfo, options, defaultOptionIndex, focusedOptionIndex, icon);
    dialog.show();
    return dialog.getExitCode();
  }

  @Override
  @RequiresEdt
  public int showTwoStepConfirmationDialog(String message,
                                           String title,
                                           String[] options,
                                           String checkboxText,
                                           boolean checked,
                                           int defaultOptionIndex,
                                           int focusedOptionIndex,
                                           Icon icon,
                                           BiFunction<? super Integer, ? super JCheckBox, Integer> exitFunc) {
    if (isApplicationInUnitTestOrHeadless()) {
      return TestDialogManager.getTestImplementation().show(message);
    }

    TwoStepConfirmationDialog dialog =
      new TwoStepConfirmationDialog(message, title, options, checkboxText, checked, defaultOptionIndex, focusedOptionIndex, icon, exitFunc);
    dialog.show();
    return dialog.getExitCode();
  }

  @Override
  @RequiresEdt
  public String showPasswordDialog(Project project, String message, String title, Icon icon, InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return TestDialogManager.getTestInputImplementation().show(message, validator);
    }

    final InputDialog dialog = project != null
                               ? new PasswordInputDialog(project, message, title, icon, validator)
                               : new PasswordInputDialog(message, title, icon, validator);
    dialog.show();
    return dialog.getInputString();
  }

  @Override
  @RequiresEdt
  public char[] showPasswordDialog(@NotNull Component parentComponent, String message, String title, Icon icon, @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return TestDialogManager.getTestInputImplementation().show(message, validator).toCharArray();
    }

    PasswordInputDialog dialog = new PasswordInputDialog(parentComponent, message, title, icon, validator);
    dialog.show();
    return dialog.getExitCode() == 0 ? getTrimmedChars(dialog.getTextField()) : null;
  }

  @Override
  @RequiresEdt
  public String showInputDialog(@Nullable Project project,
                                Component parentComponent, String message,
                                String title,
                                @Nullable Icon icon,
                                @Nullable String initialValue,
                                @Nullable InputValidator validator,
                                @Nullable TextRange selection,
                                @Nullable @NlsContexts.DetailedDescription String comment) {
    if (isApplicationInUnitTestOrHeadless()) {
      return TestDialogManager.getTestInputImplementation().show(message, validator);
    }

    InputDialog dialog = new InputDialog(project, message, title, icon, initialValue, validator,
                                         new String[]{getOkButton(), getCancelButton()},
                                         0, comment);

    final JTextComponent field = dialog.getTextField();
    if (selection != null) {
      // set custom selection
      field.select(selection.getStartOffset(), selection.getEndOffset());
      field.putClientProperty(DialogWrapperPeer.HAVE_INITIAL_SELECTION, true);
    }

    dialog.show();
    return dialog.getInputString();
  }

  @Override
  @RequiresEdt
  public String showMultilineInputDialog(Project project,
                                         String message,
                                         String title,
                                         String initialValue,
                                         Icon icon,
                                         @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return TestDialogManager.getTestInputImplementation().show(message, validator);
    }

    InputDialog dialog = new MessageMultilineInputDialog(project, message, title, icon, initialValue, validator,
                                                         new String[]{getOkButton(), getCancelButton()}, 0);
    dialog.show();
    return dialog.getInputString();
  }

  @Override
  @RequiresEdt
  public @NotNull Pair<@Nullable String, Boolean> showInputDialogWithCheckBox(String message,
                                                                              String title,
                                                                              String checkboxText,
                                                                              boolean checked,
                                                                              boolean checkboxEnabled,
                                                                              Icon icon,
                                                                              String initialValue,
                                                                              InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return new Pair<>(TestDialogManager.getTestInputImplementation().show(message), checked);
    }

    InputDialogWithCheckbox dialog =
      new InputDialogWithCheckbox(message, title, checkboxText, checked, checkboxEnabled, icon, initialValue, validator);
    dialog.show();
    return Pair.create(dialog.getInputString(), dialog.isChecked());
  }

  @Override
  @RequiresEdt
  public String showEditableChooseDialog(String message,
                                         String title,
                                         Icon icon,
                                         String[] values,
                                         @NlsSafe String initialValue,
                                         InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return TestDialogManager.getTestInputImplementation().show(message, validator);
    }

    @SuppressWarnings("deprecation") ChooseDialog dialog = new ChooseDialog(message, title, icon, values, initialValue);
    dialog.setValidator(validator);
    dialog.getComboBox().setEditable(true);
    dialog.getComboBox().getEditor().setItem(initialValue);
    dialog.getComboBox().setSelectedItem(initialValue);
    dialog.show();
    return dialog.getInputString();
  }

  @Override
  @RequiresEdt
  public int showChooseDialog(@Nullable Project project,
                              @Nullable Component parentComponent,
                              String message,
                              String title,
                              String[] values,
                              String initialValue,
                              @Nullable Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return TestDialogManager.getTestImplementation().show(message);
    }

    @SuppressWarnings("deprecation") ChooseDialog dialog = new ChooseDialog(project, parentComponent, message, title, icon, values, initialValue);
    dialog.show();
    return dialog.getSelectedIndex();
  }

  @Override
  @RequiresEdt
  public void showTextAreaDialog(final JTextField textField,
                                 String title,
                                 String dimensionServiceKey,
                                 Function<? super String, ? extends List<String>> parser,
                                 final Function<? super List<String>, String> lineJoiner) {
    if (isApplicationInUnitTestOrHeadless()) {
      TestDialogManager.getTestImplementation().show(title);
      return;
    }

    final JTextArea textArea = new JTextArea(10, 50);
    SwingUndoUtil.addUndoRedoActions(textArea);
    textArea.setWrapStyleWord(true);
    textArea.setLineWrap(true);
    List<String> lines = parser.fun(textField.getText());
    textArea.setText(StringUtil.join(lines, "\n"));
    InsertPathAction.copyFromTo(textField, textArea);
    final DialogBuilder builder = new DialogBuilder(textField);
    builder.setDimensionServiceKey(dimensionServiceKey);
    builder.setCenterPanel(ScrollPaneFactory.createScrollPane(textArea));
    builder.setPreferredFocusComponent(textArea);
    String rawText = title;
    if (StringUtil.endsWithChar(rawText, ':')) {
      rawText = rawText.substring(0, rawText.length() - 1);
    }
    builder.setTitle(rawText);
    builder.addOkAction();
    builder.addCancelAction();
    builder.setOkOperation(() -> {
      textField.setText(lineJoiner.fun(Arrays.asList(StringUtil.splitByLines(textArea.getText()))));
      builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
    });
    builder.show();
  }

  @Override
  public void showErrorDialog(@Nullable Project project,
                              @Nullable @NlsContexts.DialogMessage String message,
                              @NotNull @NlsContexts.DialogTitle String title) {
    Messages.showErrorDialog(project, message, title);
  }

  private static boolean isApplicationInUnitTestOrHeadless() {
    Application app = ApplicationManager.getApplication();
    return app != null && (app.isUnitTestMode() || app.isHeadlessEnvironment());
  }
}
