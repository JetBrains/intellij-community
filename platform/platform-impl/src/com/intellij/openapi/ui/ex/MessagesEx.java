/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.ui.ex;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MessagesEx extends Messages {

  public static MessageInfo fileIsReadOnly(Project project, String filePath) {
    return error(project, UIBundle.message("file.is.read.only.message.text", filePath));
  }

  public static MessageInfo filesAreReadOnly(Project project, String[] files) {
    if (files.length == 1){
      return fileIsReadOnly(project, files[0]);
    } else {
      return error(project, UIBundle.message("files.are.read.only.message.text", filePaths(files)));
    }
  }

  private static String filePaths(String[] files) {
    return StringUtil.join(files, ",\n");
  }

  public static MessageInfo fileIsReadOnly(Project project, VirtualFile file) {
    return fileIsReadOnly(project, file.getPresentableUrl());
  }

  public static MessageInfo error(Project project, String message) {
    return error(project, message, UIBundle.message("error.dialog.title"));
  }

  @NotNull
  public static MessageInfo error(Project project, String message, String title) {
    return new MessageInfo(project, message, title);
  }

  public abstract static class BaseDialogInfo<ThisClass extends BaseDialogInfo> {
    private final Project myProject;
    private String myMessage;
    private String myTitle;
    private Icon myIcon;
    private String[] myOptions = {CommonBundle.getOkButtonText()};
    private int myDefaultOption = 0;

    protected BaseDialogInfo(Project project) {
      myProject = project;
    }

    public BaseDialogInfo(Project project, @NotNull String message, String title, Icon icon) {
      this(project);
      myMessage = message;
      myTitle = title;
      myIcon = icon;
    }

    @NotNull
    public ThisClass setTitle(String title) {
      myTitle = title;
      return getThis();
    }

    public String getMessage() {
      return myMessage;
    }

    @NotNull
    public ThisClass appendMessage(@NotNull String message) {
      myMessage += message;
      return getThis();
    }

    public void setOptions(String[] options, int defaultOption) {
      myOptions = options;
      myDefaultOption = defaultOption;
    }

    @NotNull
    protected abstract ThisClass getThis();

    @NotNull
    public ThisClass setIcon(Icon icon) {
      myIcon = icon;
      return getThis();
    }

    public void setMessage(@NotNull String message) {
      myMessage = message;
    }

    public Project getProject() {
      return myProject;
    }

    public String getTitle() {
      return myTitle;
    }

    public String[] getOptions() {
      return myOptions;
    }

    public int getDefaultOption() {
      return myDefaultOption;
    }

    public Icon getIcon() {
      return myIcon;
    }
  }

  public static class MessageInfo extends BaseDialogInfo<MessageInfo> {
    public MessageInfo(Project project, String message, String title) {
      super(project, message, title, getErrorIcon());
    }

    public int showNow() {
      return showDialog(getProject(), getMessage(), getTitle(), getOptions(), getDefaultOption(), getIcon());
    }

    public void showLater() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            showNow();
          }
        }, ApplicationManager.getApplication().getDisposed());
    }

    @YesNoResult
    public int askYesNo() {
      setIcon(getQuestionIcon());
      return showYesNoDialog(getProject(), getMessage(), getTitle(), getIcon());
    }

    public int ask(String[] options, int defaultOptionIndex) {
      setOptions(options, defaultOptionIndex);
      return showNow();
    }

    @NotNull
    @Override
    protected MessageInfo getThis() {
      return this;
    }
  }

  public static class ChoiceInfo extends BaseInputInfo<ChoiceInfo> {
    private String[] myChoises = ArrayUtil.EMPTY_STRING_ARRAY;
    private String myDefaultChoice = null;

    public ChoiceInfo(Project project) {
      super(project);
      setIcon(getQuestionIcon());
      setOptions(new String[]{CommonBundle.getOkButtonText()}, 0);
    }

    @NotNull
    @Override
    public ChoiceInfo getThis() {
      return this;
    }

    public ChoiceInfo setChoices(String[] choices, String defaultChoice) {
      myChoises = choices;
      myDefaultChoice = defaultChoice;
      return getThis();
    }

    @Override
    public UserInput askUser() {
      ChooseDialog dialog = new ChooseDialog(getProject(), getMessage(), getTitle(), getIcon(), myChoises, myDefaultChoice, getOptions(), getDefaultOption());
      dialog.setValidator(null);
      JComboBox comboBox = dialog.getComboBox();
      comboBox.setEditable(false);
      comboBox.setSelectedItem(myDefaultChoice);
      dialog.show();
      Object selectedItem = comboBox.getSelectedItem();
      return new UserInput(selectedItem != null ? selectedItem.toString() : null, dialog.getExitCode());
    }
  }

  public static class UserInput {
    private final int mySelectedOption;
    private final String myInput;

    public UserInput(String choice, int option) {
      mySelectedOption = option;
      myInput = choice;
    }

    public String getInput() {
      return myInput;
    }

    public int getSelectedOption() {
      return mySelectedOption;
    }
  }

  public static class InputInfo extends BaseInputInfo<InputInfo> {
    private String myDefaultValue;

    public InputInfo(Project project) {
      super(project);
      setOptions(new String[]{CommonBundle.getOkButtonText(), CommonBundle.getCancelButtonText()}, 0);
    }

    @Override
    public UserInput askUser() {
      InputDialog dialog = new InputDialog(getProject(), getMessage(), getTitle(), getIcon(), myDefaultValue, null, getOptions(), getDefaultOption());
      dialog.show();
      return new UserInput(dialog.getTextField().getText(), dialog.getExitCode());
    }

    @NotNull
    @Override
    public InputInfo getThis() {
      return this;
    }

    public void setDefaultValue(String defaultValue) {
      myDefaultValue = defaultValue;
    }
  }

  public abstract static class BaseInputInfo<ThisClass extends BaseInputInfo> extends BaseDialogInfo<ThisClass> {


    public BaseInputInfo(Project project) {
      super(project);
    }

    public String forceUserInput() {
      setOptions(new String[]{CommonBundle.getOkButtonText()}, 0);
      return askUser().getInput();
    }

    public abstract UserInput askUser();
  }
}
