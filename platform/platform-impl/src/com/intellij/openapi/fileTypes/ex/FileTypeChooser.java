// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.DetectedByContentFileType;
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public final class FileTypeChooser extends DialogWrapper {
  private JList<FileType> myList;
  private JLabel myTitleLabel;
  private ComboBox<String> myPattern;
  private JPanel myPanel;
  private JRadioButton myOpenInIdea;
  private JRadioButton myOpenAsNative;
  private JRadioButton myDetectFileType;
  private JBLabel myContextHelpLabel;
  private final String myFileName;

  private FileTypeChooser(@NotNull List<String> patterns, @NotNull String fileName) {
    super(true);

    myFileName = fileName;
    myOpenInIdea.setText(FileTypesBundle.message("filetype.chooser.association", ApplicationNamesInfo.getInstance().getFullProductName()));
    myDetectFileType.setText(FileTypesBundle.message("filetype.chooser.autodetect"));
    ActionListener actionListener = e -> {
      myList.setEnabled(myOpenInIdea.isSelected());
      updateContextHelp();
    };
    myDetectFileType.addActionListener(actionListener);
    myOpenInIdea.addActionListener(actionListener);
    myOpenAsNative.addActionListener(actionListener);

    if (fileName.indexOf('.') < 0) {
      myDetectFileType.setSelected(true);
      myList.setEnabled(false);
    }

    FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    Arrays.sort(fileTypes, (ft1, ft2) -> ft1 == null ? 1 : ft2 == null ? -1 : ft1.getDescription().compareToIgnoreCase(ft2.getDescription()));

    final DefaultListModel<FileType> model = new DefaultListModel<>();
    for (FileType type : fileTypes) {
      if (!type.isReadOnly() && type != FileTypes.UNKNOWN && !(type instanceof NativeFileType) && type != DetectedByContentFileType.INSTANCE) {
        model.addElement(type);
      }
    }
    myList.setModel(model);
    myList.addListSelectionListener(e -> updateContextHelp());
    myPattern.setModel(new CollectionComboBoxModel<>(ContainerUtil.map(patterns, FunctionUtil.id()), patterns.get(0)));
    new ListSpeedSearch(myList, o -> ((FileType)o).getDescription());

    myContextHelpLabel.setForeground(UIUtil.getContextHelpForeground());
    updateContextHelp();
    setTitle(FileTypesBundle.message("filetype.chooser.title"));
    init();
  }

  private void updateContextHelp() {
    FileType selectedType = getSelectedType();
    String fileTypeString = selectedType == null ? "" : (" | " + selectedType.getDescription());
    myContextHelpLabel.setText(FileTypesBundle.message("label.help.change.association", ShowSettingsUtil.getSettingsMenuName(),
                                                       fileTypeString));
  }

  @Override
  protected JComponent createCenterPanel() {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(myFileName);
    myTitleLabel.setText(fileType == FileTypes.UNKNOWN ?
                         FileTypesBundle.message("filetype.chooser.prompt", myFileName) :
                         FileTypesBundle.message("filetype.chooser.change.prompt", myFileName, fileType.getName()));

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new FileTypeRenderer() {
      @Override
      public void customize(@NotNull JList<? extends FileType> list, FileType value, int index, boolean selected, boolean hasFocus) {
        super.customize(list, value, index, selected, hasFocus);
        if (!myOpenInIdea.isSelected()) {
          setForeground(selected ? UIUtil.getListSelectionForeground(hasFocus) : UIUtil.getComboBoxDisabledForeground());
          setBackground(selected ? UIUtil.getListSelectionBackground(hasFocus) : UIUtil.getComboBoxDisabledBackground());
        }
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        doOKAction();
        return true;
      }
    }.installOn(myList);

    myList.getSelectionModel().addListSelectionListener(__ -> updateButtonsState()
    );

    ScrollingUtil.selectItem(myList, FileTypes.PLAIN_TEXT);

    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  private void updateButtonsState() {
    setOKActionEnabled(myList.getSelectedIndex() != -1 || myOpenAsNative.isSelected() || myDetectFileType.isSelected());
  }

  @Override
  protected String getDimensionServiceKey(){
    return "#com.intellij.fileTypes.FileTypeChooser";
  }

  public FileType getSelectedType() {
    if (myDetectFileType.isSelected()) return DetectedByContentFileType.INSTANCE;
    return myOpenAsNative.isSelected() ? NativeFileType.INSTANCE : myList.getSelectedValue();
  }

  /**
   * If fileName is already associated any known file type returns it.
   * Otherwise asks user to select file type and associates it with fileName extension if any selected.
   * @return Known file type or null. Never returns {@link FileTypes#UNKNOWN}.
   */
  @Nullable
  public static FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file, @Nullable Project project) {
    if (project != null && !(file instanceof FakeVirtualFile)) {
      PsiManagerEx.getInstanceEx(project).getFileManager().findFile(file); // autodetect text file if needed
    }
    FileType type = file.getFileType();
    if (type == FileTypes.UNKNOWN) {
      type = getKnownFileTypeOrAssociate(file.getName());
    }
    return type;
  }

  /**
   * Speculates if file with newName would have known file type
   */
  @Nullable
  public static FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile parent, @NotNull String newName, @Nullable Project project) {
    return getKnownFileTypeOrAssociate(new FakeVirtualFile(parent, newName), project);
  }

  @Nullable
  public static FileType getKnownFileTypeOrAssociate(@NotNull String fileName) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType type = fileTypeManager.getFileTypeByFileName(fileName);
    if (type == FileTypes.UNKNOWN) {
      type = associateFileType(fileName);
    }
    return type;
  }

  @Nullable
  public static FileType associateFileType(@NotNull final String fileName) {
    final FileTypeChooser chooser = new FileTypeChooser(suggestPatterns(fileName), fileName);
    if (!chooser.showAndGet()) {
      return null;
    }
    final FileType type = chooser.getSelectedType();
    if (type == FileTypes.UNKNOWN || type == null) return null;

    ApplicationManager.getApplication().runWriteAction(() -> FileTypeManagerEx.getInstanceEx().associatePattern(type, chooser.getSelectedPattern()));

    return type;
  }

  private String getSelectedPattern() {
    return (String)myPattern.getSelectedItem();
  }

  @NotNull
  static List<String> suggestPatterns(@NotNull String fileName) {
    List<String> patterns = new LinkedList<>();

    int i = -1;
    while ((i = fileName.indexOf('.', i + 1)) > 0) {
      String extension = fileName.substring(i);
      if (!StringUtil.isEmpty(extension)) {
        patterns.add(0, "*" + extension);
      }
    }
    if (FileTypeManager.getInstance().getFileTypeByFileName(fileName) == FileTypes.UNKNOWN) {
      patterns.add(fileName);
    }
    else {
      patterns.add(0, fileName);
    }

    return patterns;
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.register.association";
  }
}