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
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.TextFieldCompletionProvider;
import com.intellij.util.TextFieldCompletionProviderDumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.mvc.util.ModuleCellRenderer;
import org.jetbrains.plugins.groovy.mvc.util.MvcTargetDialogCompletionUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class MvcRunTargetDialog extends DialogWrapper {

  private static final String GRAILS_PREFIX = "grails ";

  private JPanel contentPane;
  private JLabel myTargetLabel;
  private JPanel myFakePanel;
  private EditorTextField myVmOptionsField;
  private JComboBox myModuleBox;
  private JLabel myModuleLabel;
  private JLabel myVmOptionLabel;
  private ComboBox myTargetField;
  private Module myModule;

  private final MvcFramework myFramework;

  private Action myInteractiveRunAction;

  public MvcRunTargetDialog(@NotNull Module module, @NotNull MvcFramework framework) {
    super(module.getProject(), true);
    myModule = module;
    myFramework = framework;
    setTitle("Run " + framework.getDisplayName() + " target");
    setUpDialog();
    setModal(true);
    init();
  }

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    boolean hasOneSupportedModule = false;
    for (Module module : ModuleManager.getInstance(myModule.getProject()).getModules()) {
      if (module == myModule || myFramework.hasSupport(module)) {
        if (myFramework.isInteractiveConsoleSupported(module)) {
          hasOneSupportedModule = true;
          break;
        }
      }
    }

    if (hasOneSupportedModule) {
      myInteractiveRunAction = new DialogWrapperAction("&Start Grails Console in Interactive Mode") {
        @Override
        protected void doAction(ActionEvent e) {
          myFramework.runInteractiveConsole(getSelectedModule());
          doCancelAction();
        }
      };

      myInteractiveRunAction.setEnabled(myFramework.isInteractiveConsoleSupported(myModule));

      return new Action[]{myInteractiveRunAction};
    }

    return new Action[0];
  }

  private void setUpDialog() {
    myTargetLabel.setLabelFor(myTargetField);
    myTargetField.setFocusable(true);

    myVmOptionLabel.setLabelFor(myVmOptionsField);
    myVmOptionsField.setText(MvcRunTargetHistoryService.getInstance().getVmOptions());

    List<Module> mvcModules = new ArrayList<Module>();
    for (Module module : ModuleManager.getInstance(myModule.getProject()).getModules()) {
      if (module == myModule || myFramework.hasSupport(module)) {
        mvcModules.add(module);
      }
    }

    assert mvcModules.contains(myModule);

    myModuleLabel.setLabelFor(myModuleBox);
    myModuleBox.setModel(new CollectionComboBoxModel(mvcModules, myModule));
    myModuleBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myModule = (Module)myModuleBox.getSelectedItem();
        if (myInteractiveRunAction != null) {
          myInteractiveRunAction.setEnabled(myFramework.isInteractiveConsoleSupported(myModule));
        }
      }
    });

    myModuleBox.setRenderer(new ModuleCellRenderer(myModuleBox.getRenderer()));
  }

  @NotNull
  public Module getSelectedModule() {
    return myModule;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    MvcRunTargetHistoryService.getInstance().addCommand(getSelectedText(), getVmOptions());
  }

  public String getVmOptions() {
    return myVmOptionsField.getText();
  }

  public String getSelectedText() {
    return (String)myTargetField.getEditor().getItem();
  }

  @NotNull
  public String getTargetArguments() {
    String text = getSelectedText();

    text = text.trim();
    if (text.startsWith(GRAILS_PREFIX)) {
      text = text.substring(GRAILS_PREFIX.length());
    }

    return text;
  }

  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTargetField;
  }

  private void createUIComponents() {
    myTargetField = new ComboBox(MvcRunTargetHistoryService.getInstance().getHistory(), -1);
    myTargetField.setLightWeightPopupEnabled(false);

    EditorComboBoxEditor editor = new StringComboboxEditor(myModule.getProject(), PlainTextFileType.INSTANCE, myTargetField);
    myTargetField.setRenderer(new EditorComboBoxRenderer(editor));

    myTargetField.setEditable(true);
    myTargetField.setEditor(editor);

    EditorTextField editorTextField = editor.getEditorComponent();

    myFakePanel = new JPanel(new BorderLayout());
    myFakePanel.add(myTargetField, BorderLayout.CENTER);

    TextFieldCompletionProvider vmOptionCompletionProvider = new TextFieldCompletionProviderDumbAware() {
      @NotNull
      @Override
      protected String getPrefix(@NotNull String currentTextPrefix) {
        return MvcRunTargetDialog.getPrefix(currentTextPrefix);
      }

      @Override
      protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
        if (prefix.endsWith("-D")) {
          result.addAllElements(MvcTargetDialogCompletionUtils.getSystemPropertiesVariants());
        }
      }
    };
    myVmOptionsField = vmOptionCompletionProvider.createEditor(myModule.getProject());

    new TextFieldCompletionProviderDumbAware() {

      @NotNull
      @Override
      protected String getPrefix(@NotNull String currentTextPrefix) {
        return MvcRunTargetDialog.getPrefix(currentTextPrefix);
      }

      @Override
      protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
        for (LookupElement variant : MvcTargetDialogCompletionUtils.collectVariants(myModule, text, offset, prefix)) {
          result.addElement(variant);
        }
      }
    }.apply(editorTextField);

    editorTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        setOKActionEnabled(!StringUtil.isEmptyOrSpaces(e.getDocument().getText()));
      }
    });
    setOKActionEnabled(false);
  }

  public static String getPrefix(String currentTextPrefix) {
    return currentTextPrefix.substring(currentTextPrefix.lastIndexOf(' ') + 1);
  }

}
