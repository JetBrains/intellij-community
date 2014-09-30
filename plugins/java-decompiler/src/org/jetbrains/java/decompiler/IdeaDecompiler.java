/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler;

import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

public class IdeaDecompiler extends ClassFileDecompilers.Light {
  public static final String BANNER =
    "//\n" +
    "// Source code recreated from a .class file by IntelliJ IDEA\n" +
    "// (powered by Fernflower decompiler)\n" +
    "//\n\n";

  private static final String LEGAL_NOTICE_KEY = "decompiler.legal.notice.accepted";

  private final IFernflowerLogger myLogger = new IdeaLogger();
  private final Map<String, Object> myOptions = new HashMap<String, Object>();
  private boolean myLegalNoticeAccepted;

  public IdeaDecompiler() {
    myOptions.put(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR, "0");
    myOptions.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
    myOptions.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
    myOptions.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
    myOptions.put(IFernflowerPreferences.LITERALS_AS_IS, "1");
    myOptions.put(IFernflowerPreferences.NEW_LINE_SEPARATOR, "1");

    Project project = DefaultProjectFactory.getInstance().getDefaultProject();
    CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);
    myOptions.put(IFernflowerPreferences.INDENT_STRING, StringUtil.repeat(" ", options.INDENT_SIZE));

    Application app = ApplicationManager.getApplication();
    myLegalNoticeAccepted = app.isUnitTestMode() || PropertiesComponent.getInstance().isValueSet(LEGAL_NOTICE_KEY);
    if (!myLegalNoticeAccepted) {
      MessageBusConnection connection = app.getMessageBus().connect(app);
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
        @Override
        public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
          if (file.getFileType() == StdFileTypes.CLASS) {
            FileEditor editor = source.getSelectedEditor(file);
            if (editor != null) {
              showLegalNotice(source.getProject(), file);
            }
          }
        }
      });
    }
  }

  private void showLegalNotice(final Project project, final VirtualFile file) {
    if (!myLegalNoticeAccepted) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          new LegalNoticeDialog(project, file).show();
        }
      }, ModalityState.NON_MODAL);
    }
  }

  @Override
  public boolean accepts(@NotNull VirtualFile file) {
    return true;
  }

  @NotNull
  @Override
  public CharSequence getText(@NotNull VirtualFile file) throws CannotDecompileException {
    if ("package-info.class".equals(file.getName())) {
      return ClsFileImpl.decompile(file);
    }

    try {
      Map<String, VirtualFile> files = ContainerUtil.newLinkedHashMap();
      files.put(file.getPath(), file);
      String mask = file.getNameWithoutExtension() + "$";
      for (VirtualFile child : file.getParent().getChildren()) {
        if (child.getNameWithoutExtension().startsWith(mask) && file.getFileType() == StdFileTypes.CLASS) {
          files.put(child.getPath(), child);
        }
      }
      MyBytecodeProvider provider = new MyBytecodeProvider(files);
      MyResultSaver saver = new MyResultSaver();

      BaseDecompiler decompiler = new BaseDecompiler(provider, saver, myOptions, myLogger);
      for (String path : files.keySet()) {
        decompiler.addSpace(new File(path), true);
      }
      decompiler.decompileContext();

      return BANNER + saver.myResult;
    }
    catch (Exception e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        AssertionError error = new AssertionError(file.getUrl());
        error.initCause(e);
        throw error;
      }
      else {
        throw new CannotDecompileException(e);
      }
    }
  }

  private static class MyBytecodeProvider implements IBytecodeProvider {
    private final Map<String, VirtualFile> myFiles;

    private MyBytecodeProvider(@NotNull Map<String, VirtualFile> files) {
      myFiles = files;
    }

    @Override
    public byte[] getBytecode(String externalPath, String internalPath) {
      try {
        String path = FileUtil.toSystemIndependentName(externalPath);
        VirtualFile file = myFiles.get(path);
        assert file != null : path;
        return file.contentsToByteArray();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class MyResultSaver implements IResultSaver {
    private String myResult = "";

    @Override
    public void saveClassFile(String path, String qualifiedName, String entryName, String content) {
      if (myResult.isEmpty()) {
        myResult = content;
      }
    }

    @Override
    public void saveFolder(String path) { }

    @Override
    public void copyFile(String source, String path, String entryName) { }

    @Override
    public void createArchive(String path, String archiveName, Manifest manifest) { }

    @Override
    public void saveDirEntry(String path, String archiveName, String entryName) { }

    @Override
    public void copyEntry(String source, String path, String archiveName, String entry) { }

    @Override
    public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) { }

    @Override
    public void closeArchive(String path, String archiveName) { }
  }

  private class LegalNoticeDialog extends DialogWrapper {
    private final Project myProject;
    private final VirtualFile myFile;
    private JEditorPane myMessage;

    public LegalNoticeDialog(Project project, VirtualFile file) {
      super(project);
      myProject = project;
      myFile = file;
      setTitle(IdeaDecompilerBundle.message("legal.notice.title"));
      setOKButtonText(IdeaDecompilerBundle.message("legal.notice.action.accept"));
      setCancelButtonText(IdeaDecompilerBundle.message("legal.notice.action.postpone"));
      init();
      pack();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel iconPanel = new JBPanel(new BorderLayout());
      iconPanel.add(new JBLabel(AllIcons.General.WarningDialog), BorderLayout.NORTH);

      myMessage = new JEditorPane();
      myMessage.setEditorKit(UIUtil.getHTMLEditorKit());
      myMessage.setEditable(false);
      myMessage.setPreferredSize(new Dimension(500, 100));
      myMessage.setBorder(BorderFactory.createLineBorder(Gray._200));
      String text = "<div style='margin:5px;'>" + IdeaDecompilerBundle.message("legal.notice.text") + "</div>";
      myMessage.setText(text);

      JPanel panel = new JBPanel(new BorderLayout(10, 0));
      panel.add(iconPanel, BorderLayout.WEST);
      panel.add(myMessage, BorderLayout.CENTER);
      return panel;
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      DialogWrapperAction decline = new DialogWrapperAction(IdeaDecompilerBundle.message("legal.notice.action.reject")) {
        @Override
        protected void doAction(ActionEvent e) {
          doDeclineAction();
        }
      };
      return new Action[]{getOKAction(), decline, getCancelAction()};
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myMessage;
    }

    @Override
    protected void doOKAction() {
      super.doOKAction();
      PropertiesComponent.getInstance().setValue(LEGAL_NOTICE_KEY, Boolean.TRUE.toString());
      myLegalNoticeAccepted = true;
    }

    private void doDeclineAction() {
      doCancelAction();
      PluginManagerCore.disablePlugin("org.jetbrains.java.decompiler");
      ApplicationManagerEx.getApplicationEx().restart(true);
    }

    @Override
    public void doCancelAction() {
      super.doCancelAction();
      FileEditorManager.getInstance(myProject).closeFile(myFile);
    }
  }
}
