package org.jetbrains.android.logcat;

import com.android.ddmlib.Log;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.keymap.KeymapUtil;
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
import java.util.Comparator;
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

    myTagField = new MyTextFieldWithAutoCompletion(view.getProject()) {
      @Override
      public void showLookup() {
        parseExistingMessagesIfNecessary();
        myTagField.setVariants(Arrays.asList(toLookupElements(myUsedTags)));
        super.showLookup();
      }
    };

    myTagFieldWrapper.add(myTagField);
    myLogTagLabel.setLabelFor(myTagField);

    myPidField = new MyTextFieldWithAutoCompletion(view.getProject()) {
      @Override
      public void showLookup() {
        parseExistingMessagesIfNecessary();
        myPidField.setVariants(myUsedPids);
        super.showLookup();
      }
    };
    myPidField.setComparator(new Comparator<LookupElement>() {
      @Override
      public int compare(LookupElement e1, LookupElement e2) {
        final int pid1 = Integer.parseInt(e1.getLookupString());
        final int pid2 = Integer.parseInt(e2.getLookupString());
        return Comparing.compare(pid1, pid2);
      }
    });
    myPidFieldWrapper.add(myPidField);

    myLogLevelCombo.setModel(new EnumComboBoxModel<Log.LogLevel>(Log.LogLevel.class));
    myLogLevelCombo.setRenderer(new ListCellRendererWrapper(myLogLevelCombo.getRenderer()) {
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

  @Nullable
  private static String getCompletionShortcutText() {
    final AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION);
    if (action != null) {
      final ShortcutSet shortcutSet = action.getShortcutSet();
      if (shortcutSet != null) {
        final Shortcut[] shortcuts = shortcutSet.getShortcuts();
        if (shortcuts != null && shortcuts.length > 0) {
          return KeymapUtil.getShortcutText(shortcuts[0]);
        }
      }
    }
    return null;
  }
  
  private static LookupElement[] toLookupElements(@NotNull String[] strs) {
    final LookupElement[] result = new LookupElement[strs.length];
    
    for (int i = 0; i < result.length; i++) {
      final String s = strs[i];
      result[i] = LookupElementBuilder.create(s).addLookupString(s.toLowerCase());
    }
    return result;
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

      final String tag = matcher.group(1).trim();
      if (tag != null && tag.length() > 0) {
        tagSet.add(tag);
      }

      final String pid = matcher.group(2).trim();
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

  private static class MyTextFieldWithAutoCompletion extends TextFieldWithAutoCompletion {
    private boolean myToShowHint;
    private final String myCompletionShortcutText;

    public MyTextFieldWithAutoCompletion(Project project) {
      super(project);
      myCompletionShortcutText = getCompletionShortcutText();
      myToShowHint = true;
    }

    @Override
    protected EditorEx createEditor() {
      final EditorEx editor = super.createEditor();

      if (myCompletionShortcutText == null) {
        return editor;
      }

      editor.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        public void documentChanged(DocumentEvent e) {
          myToShowHint = false;
        }
      });

      editor.addFocusListener(new FocusChangeListener() {
        @Override
        public void focusGained(final Editor editor) {
          if (myToShowHint && getText().length() == 0) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                HintManager.getInstance().showInformationHint(editor, "Code completion available (" + myCompletionShortcutText + ")");
              }
            });
          }
        }

        @Override
        public void focusLost(Editor editor) {
        }
      });

      return editor;
    }
  }
}
