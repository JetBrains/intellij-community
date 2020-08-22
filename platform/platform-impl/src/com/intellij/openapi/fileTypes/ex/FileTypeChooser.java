// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer;
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
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
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
  private final String myFileName;

  private FileTypeChooser(@NotNull List<String> patterns, @NotNull String fileName) {
    super(true);

    myFileName = fileName;
    myOpenInIdea.setText(FileTypesBundle.message("filetype.chooser.association", ApplicationNamesInfo.getInstance().getFullProductName()));

    FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    Arrays.sort(fileTypes, (ft1, ft2) -> ft1 == null ? 1 : ft2 == null ? -1 : ft1.getDescription().compareToIgnoreCase(ft2.getDescription()));

    final DefaultListModel<FileType> model = new DefaultListModel<>();
    for (FileType type : fileTypes) {
      if (!type.isReadOnly() && type != FileTypes.UNKNOWN && !(type instanceof NativeFileType)) {
        model.addElement(type);
      }
    }
    myList.setModel(model);
    myPattern.setModel(new CollectionComboBoxModel<>(ContainerUtil.map(patterns, FunctionUtil.id()), patterns.get(0)));
    new ListSpeedSearch(myList, (Function<Object, String>)o -> ((FileType)o).getDescription());

    setTitle(FileTypesBundle.message("filetype.chooser.title"));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(myFileName);
    myTitleLabel.setText(fileType == FileTypes.UNKNOWN ?
                         FileTypesBundle.message("filetype.chooser.prompt", myFileName) :
                         FileTypesBundle.message("filetype.chooser.change.prompt", myFileName, fileType.getName()));

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new FileTypeRenderer());

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        doOKAction();
        return true;
      }
    }.installOn(myList);

    myList.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          updateButtonsState();
        }
      }
    );

    ScrollingUtil.selectItem(myList, FileTypes.PLAIN_TEXT);

    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  private void updateButtonsState() {
    setOKActionEnabled(myList.getSelectedIndex() != -1 || myOpenAsNative.isSelected());
  }

  @Override
  protected String getDimensionServiceKey(){
    return "#com.intellij.fileTypes.FileTypeChooser";
  }

  public FileType getSelectedType() {
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

    ApplicationManager.getApplication().runWriteAction(() -> FileTypeManagerEx.getInstanceEx().associatePattern(type, (String)chooser.myPattern.getSelectedItem()));

    return type;
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