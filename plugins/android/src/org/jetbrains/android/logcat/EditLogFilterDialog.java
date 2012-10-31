/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android.logcat;

import com.android.ddmlib.Log;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Eugene.Kudelevsky
 */
class EditLogFilterDialog extends DialogWrapper {
  private JPanel myContentPanel;
  private JTextField myNameField;
  private TextFieldWithAutoCompletion myTagField;
  private JTextField myMessageField;
  private TextFieldWithAutoCompletion myPidField;
  private JComboBox myLogLevelCombo;
  private JPanel myTagFieldWrapper;
  private JPanel myPidFieldWrapper;
  private JBLabel myLogTagLabel;

  private final AndroidConfiguredLogFilters.MyFilterEntry myEntry;
  private final AndroidLogcatToolWindowView myView;

  private boolean myExistingMessagesParsed = false;

  private String[] myUsedTags;
  private String[] myUsedPids;

  protected EditLogFilterDialog(@NotNull final AndroidLogcatToolWindowView view,
                                @Nullable AndroidConfiguredLogFilters.MyFilterEntry entry) {
    super(view.getProject(), false);

    myView = view;

    final Project project = view.getProject();

    myTagField = new TextFieldWithAutoCompletion<String>(project, new TextFieldWithAutoCompletion.StringsCompletionProvider(null, null) {
      @NotNull
      @Override
      public Collection<String> getItems(String prefix, boolean cached, CompletionParameters parameters) {
        parseExistingMessagesIfNecessary();
        setItems(Arrays.asList(myUsedTags));
        return super.getItems(prefix, cached, parameters);
      }
    }, true, null);

    myTagFieldWrapper.add(myTagField);
    myLogTagLabel.setLabelFor(myTagField);

    myPidField = new TextFieldWithAutoCompletion<String>(project, new TextFieldWithAutoCompletion.StringsCompletionProvider(null, null) {
      @NotNull
      @Override
      public Collection<String> getItems(String prefix, boolean cached, CompletionParameters parameters) {
        parseExistingMessagesIfNecessary();
        setItems(Arrays.asList(myUsedPids));
        return super.getItems(prefix, cached, parameters);
      }

      @Override
      public int compare(String item1, String item2) {
        final int pid1 = Integer.parseInt(item1);
        final int pid2 = Integer.parseInt(item2);
        return Comparing.compare(pid1, pid2);
      }
    }, true, null);
    myPidFieldWrapper.add(myPidField);

    myLogLevelCombo.setModel(new EnumComboBoxModel<Log.LogLevel>(Log.LogLevel.class));
    myLogLevelCombo.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          setText(StringUtil.capitalize(((Log.LogLevel)value).getStringValue().toLowerCase()));
        }
      }
    });

    init();

    if (entry != null) {
      myEntry = entry;
      reset();
    }
    else {
      myEntry = new AndroidConfiguredLogFilters.MyFilterEntry();
    }
  }

  private void parseExistingMessagesIfNecessary() {
    if (myExistingMessagesParsed) {
      return;
    }
    myExistingMessagesParsed = true;

    final StringBuffer document = myView.getLogConsole().getOriginalDocument();
    if (document == null) {
      return;
    }

    final Set<String> tagSet = new HashSet<String>();
    final Set<String> pidSet = new HashSet<String>();

    final String[] lines = StringUtil.splitByLines(document.toString());
    for (String line : lines) {
      final Matcher matcher = AndroidLogFilterModel.ANDROID_LOG_MESSAGE_PATTERN.matcher(line);

      if (!matcher.matches()) {
        continue;
      }

      final String tag = matcher.group(2).trim();
      if (tag != null && tag.length() > 0) {
        tagSet.add(tag);
      }

      final String pid = matcher.group(3).trim();
      if (pid != null && pid.length() > 0) {
        try {
          Integer.parseInt(pid);
          pidSet.add(pid);
        }
        catch (NumberFormatException ignored) {
        }
      }
    }

    myUsedTags = tagSet.toArray(new String[tagSet.size()]);
    myUsedPids = pidSet.toArray(new String[pidSet.size()]);
  }

  private void reset() {
    myNameField.setText(myEntry.getName());
    myTagField.setText(myEntry.getLogTagPattern());
    myMessageField.setText(myEntry.getLogMessagePattern());
    myPidField.setText(myEntry.getPid());

    final String logLevelStr = myEntry.getLogLevel();
    final Log.LogLevel logLevel = Log.LogLevel.getByString(logLevelStr);
    myLogLevelCombo.setSelectedItem(logLevel != null ? logLevel : Log.LogLevel.VERBOSE);
  }

  private void apply() {
    myEntry.setName(myNameField.getText().trim());
    myEntry.setLogTagPattern(myTagField.getText().trim());
    myEntry.setLogMessagePattern(myMessageField.getText().trim());
    myEntry.setPid(myPidField.getText().trim());
    myEntry.setLogLevel(((Log.LogLevel)myLogLevelCombo.getSelectedItem()).getStringValue());
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField.getText().length() == 0 ? myNameField : myTagField;
  }

  @Override
  protected void doOKAction() {
    apply();
    super.doOKAction();
  }

  @Override
  protected ValidationInfo doValidate() {
    final String name = myNameField.getText().trim();

    if (name.length() == 0) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.name.not.specified.error"), myNameField);
    }

    if (name.equals(AndroidLogcatToolWindowView.EMPTY_CONFIGURED_FILTER)) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.name.busy.error", name));
    }

    final AndroidConfiguredLogFilters.MyFilterEntry entry =
      AndroidConfiguredLogFilters.getInstance(myView.getProject()).findFilterEntryByName(name);
    if (entry != null && entry != myEntry) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.name.busy.error", name));
    }

    try {
      final String tagPattern = myTagField.getText().trim();
      if (tagPattern.length() > 0) {
        Pattern.compile(tagPattern, AndroidConfiguredLogFilters.getPatternCompileFlags(tagPattern));
      }
    }
    catch (PatternSyntaxException e) {
      final String message = e.getMessage();
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.incorrect.log.tag.pattern.error") +
                                (message != null ? ('\n' + message) : ""));
    }

    try {
      final String messagePattern = myMessageField.getText().trim();
      if (messagePattern.length() > 0) {
        Pattern.compile(messagePattern, AndroidConfiguredLogFilters.getPatternCompileFlags(messagePattern));
      }
    }
    catch (PatternSyntaxException e) {
      final String message = e.getMessage();
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.incorrect.message.pattern.error") +
                                (message != null ? ('\n' + message) : ""));
    }

    boolean validPid = false;
    try {
      final String pidStr = myPidField.getText().trim();
      final Integer pid = pidStr.length() > 0 ? Integer.parseInt(pidStr) : null;

      if (pid == null || pid.intValue() >= 0) {
        validPid = true;
      }
    }
    catch (NumberFormatException ignored) {
    }
    if (!validPid) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.incorrect.pid.error"));
    }

    return null;
  }

  @NotNull
  public AndroidConfiguredLogFilters.MyFilterEntry getCustomLogFiltersEntry() {
    return myEntry;
  }
}
