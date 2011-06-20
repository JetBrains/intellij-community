package org.jetbrains.plugins.groovy.mvc;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.TextFieldCompletionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.mvc.util.ModuleCellRenderer;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MvcRunTargetDialog extends DialogWrapper {

  private JPanel contentPane;
  private JLabel myTargetLabel;
  @SuppressWarnings({"UnusedDeclaration"}) private JPanel myFakePanel;
  private JTextField myVmOptionsField;
  private JComboBox myModuleBox;
  private JLabel myModuleLabel;
  private JLabel myVmOptionLabel;
  private ComboBox myTargetField;
  private Module myModule;
  private final MvcFramework myFramework;

  private Set<String> myCompletionVariantCache = null;

  public MvcRunTargetDialog(@NotNull Module module, MvcFramework framework) {
    super(module.getProject(), true);
    myModule = module;
    myFramework = framework;
    setTitle("Run " + framework.getDisplayName() + " target");
    setUpDialog();
    setModal(true);
    init();
  }

  private void setUpDialog() {
    myTargetLabel.setLabelFor(myTargetField);
    myTargetField.setFocusable(true);

    myVmOptionLabel.setLabelFor(myVmOptionsField);
    myVmOptionsField.setText(MvcRunTargetHistoryService.getInstance().getVmOptions());

    List<Module> mvcModules = new ArrayList<Module>();
    for (Module module : ModuleManager.getInstance(myModule.getProject()).getModules()) {
      if (module == myModule || MvcModuleStructureSynchronizer.getFramework(module) != null) {
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
        myCompletionVariantCache = null;
      }
    });

    myModuleBox.setRenderer(new ModuleCellRenderer());
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

  public String[] getTargetArguments() {
    String text = getSelectedText();
    Iterable<String> iterable = StringUtil.tokenize(text, " ");
    ArrayList<String> args = new ArrayList<String>();
    for (String s : iterable) {
      args.add(s);
    }
    return ArrayUtil.toStringArray(args);
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

    EditorTextField editorTextField = (EditorTextField)editor.getEditorComponent();

    myFakePanel = new JPanel(new BorderLayout());
    myFakePanel.add(myTargetField, BorderLayout.CENTER);

    new TextFieldCompletionProvider() {

      @NotNull
      @Override
      protected String getPrefix(@NotNull String currentTextPrefix) {
        return currentTextPrefix.substring(currentTextPrefix.lastIndexOf(' ') + 1);
      }

      @Override
      protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
        if (myCompletionVariantCache == null) {
          myCompletionVariantCache = getAllTargetNames(myModule);
        }

        for (String completionVariant : myCompletionVariantCache) {
          result.addElement(LookupElementBuilder.create(completionVariant));
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

  private Set<String> getAllTargetNames(@NotNull Module module) {
    final Set<String> result = new HashSet<String>();

    MvcFramework.addAvailableSystemScripts(result, module);

    final VirtualFile root = myFramework.findAppRoot(module);
    if (root != null) {
      MvcFramework.addAvailableScripts(result, root);
    }

    for (VirtualFile pluginRoot : myFramework.getAllPluginRoots(module, false)) {
      MvcFramework.addAvailableScripts(result, pluginRoot);
    }

    addScriptsFromUserHome(result);

    return result;
  }

  private static void addScriptsFromUserHome(Set<String> result) {
    String userHome = SystemProperties.getUserHome();
    if (userHome == null) return;

    File scriptFolder = new File(userHome, ".grails/scripts");

    File[] files = scriptFolder.listFiles();

    if (files == null) return;

    for (File file : files) {
      if (isScriptFile(file)) {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        if (idx != -1) {
          name = name.substring(0, idx);
        }

        result.add(GroovyNamesUtil.camelToSnake(name));
      }
    }
  }

  public static boolean isScriptFile(File file) {
    return file.isFile() && MvcFramework.isScriptFileName(file.getName());
  }
}
