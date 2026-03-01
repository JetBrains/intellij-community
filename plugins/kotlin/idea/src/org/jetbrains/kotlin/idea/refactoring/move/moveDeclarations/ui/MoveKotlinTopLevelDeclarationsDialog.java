// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.ui;

import com.intellij.ide.util.DirectoryChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.AbstractMemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle;
import org.jetbrains.kotlin.idea.core.util.PhysicalFileSystemUtilsKt;
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSettings;
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo;
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberSelectionPanel;
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberSelectionTable;
import org.jetbrains.kotlin.idea.refactoring.move.MoveUtilKt;
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinDestinationFolderComboBox;
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinFileChooserDialog;
import org.jetbrains.kotlin.idea.util.ExpectActualUtilKt;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedDeclaration;
import org.jetbrains.kotlin.psi.KtPureElement;
import org.jetbrains.kotlin.psi.psiUtil.KtPsiUtilKt;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.io.File;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

import static org.jetbrains.kotlin.idea.roots.ProjectRootUtilsKt.getSuitableDestinationSourceRoots;

public class MoveKotlinTopLevelDeclarationsDialog extends RefactoringDialog {
  private static final String RECENTS_KEY = "MoveKotlinTopLevelDeclarationsDialog.RECENTS_KEY";
  private final MoveCallback moveCallback;
  private final PsiDirectory initialTargetDirectory;
  private final JCheckBox cbSearchInComments;
  private final JCheckBox cbSearchTextOccurrences;
  private final JPanel mainPanel;
  private final ReferenceEditorComboWithBrowseButton classPackageChooser;
  private final ComboboxWithBrowseButton destinationFolderCB;
  private final JPanel targetPanel;
  private final JRadioButton rbMoveToPackage;
  private final JRadioButton rbMoveToFile;
  private final TextFieldWithBrowseButton fileChooser;
  private final JPanel memberInfoPanel;
  private final JTextField tfFileNameInPackage;
  private final JCheckBox cbDeleteEmptySourceFiles;
  private final JCheckBox cbSearchReferences;
  private final JCheckBox cbApplyMPPDeclarationsMove;
  private KotlinMemberSelectionTable memberTable;

  private final boolean freezeTargets;

  public MoveKotlinTopLevelDeclarationsDialog(
    @NotNull Project project,
    @NotNull Set<KtNamedDeclaration> elementsToMove,
    @Nullable String targetPackageName,
    @Nullable PsiDirectory targetDirectory,
    @Nullable KtFile targetFile,
    boolean freezeTargets,
    boolean moveToPackage,
    @Nullable MoveCallback moveCallback
  ) {
    this(project,
         elementsToMove,
         targetPackageName,
         targetDirectory,
         targetFile,
         freezeTargets,
         moveToPackage,
         KotlinRefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS,
         KotlinRefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT,
         KotlinRefactoringSettings.getInstance().MOVE_DELETE_EMPTY_SOURCE_FILES,
         KotlinRefactoringSettings.getInstance().MOVE_MPP_DECLARATIONS,
         moveCallback);
  }

  public MoveKotlinTopLevelDeclarationsDialog(
    @NotNull Project project,
    @NotNull Set<KtNamedDeclaration> elementsToMove,
    @Nullable String targetPackageName,
    @Nullable PsiDirectory targetDirectory,
    @Nullable KtFile targetFile,
    boolean freezeTargets,
    boolean moveToPackage,
    boolean searchInComments,
    boolean searchForTextOccurrences,
    boolean deleteEmptySourceFiles,
    boolean moveMppDeclarations,
    @Nullable MoveCallback moveCallback
  ) {
    super(project, true);
    this.freezeTargets = freezeTargets;
    {
      classPackageChooser = createPackageChooser();

      destinationFolderCB = new KotlinDestinationFolderComboBox() {
        @Override
        public String getTargetPackage() {
          return MoveKotlinTopLevelDeclarationsDialog.this.getTargetPackage();
        }

        @Override
        protected boolean sourceRootsInTargetDirOnly() {
          return !freezeTargets;
        }
      };
    }
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      mainPanel = new JPanel();
      mainPanel.setLayout(new BorderLayout(0, 0));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new BorderLayout(0, 0));
      mainPanel.add(panel1, BorderLayout.CENTER);
      memberInfoPanel = new JPanel();
      memberInfoPanel.setLayout(new BorderLayout(0, 0));
      panel1.add(memberInfoPanel, BorderLayout.CENTER);
      final JPanel panel2 = new JPanel();
      panel2.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
      panel1.add(panel2, BorderLayout.SOUTH);
      final JPanel panel3 = new JPanel();
      panel3.setLayout(new GridLayoutManager(6, 2, new Insets(7, 0, 7, 0), -1, -1));
      panel2.add(panel3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                             0, false));
      panel3.add(classPackageChooser, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      rbMoveToPackage = new JRadioButton();
      this.$$$loadButtonText$$$(rbMoveToPackage, this.$$$getMessageFromBundle$$$("messages/KotlinBundle", "label.text.to.package"));
      panel3.add(rbMoveToPackage,
                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      fileChooser = new TextFieldWithBrowseButton();
      panel3.add(fileChooser, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      rbMoveToFile = new JRadioButton();
      this.$$$loadButtonText$$$(rbMoveToFile, this.$$$getMessageFromBundle$$$("messages/KotlinBundle", "label.text.to.file"));
      panel3.add(rbMoveToFile,
                 new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final JLabel label1 = new JLabel();
      this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/KotlinBundle", "label.text.file.name"));
      panel3.add(label1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 5,
                                             false));
      tfFileNameInPackage = new JTextField();
      tfFileNameInPackage.setEnabled(false);
      panel3.add(tfFileNameInPackage, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                          new Dimension(150, -1), null, 0, false));
      targetPanel = new JPanel();
      targetPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
      panel3.add(targetPanel, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 5, false));
      final JLabel label2 = new JLabel();
      this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/KotlinBundle", "label.text.destination.directory"));
      targetPanel.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                  false));
      targetPanel.add(destinationFolderCB, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final JPanel panel4 = new JPanel();
      panel4.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
      panel3.add(panel4, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                             0, false));
      cbSearchInComments = new NonFocusableCheckBox();
      cbSearchInComments.setSelected(true);
      this.$$$loadButtonText$$$(cbSearchInComments,
                                this.$$$getMessageFromBundle$$$("messages/KotlinBundle", "search.in.comments.and.strings"));
      panel4.add(cbSearchInComments,
                 new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      cbSearchTextOccurrences = new NonFocusableCheckBox();
      cbSearchTextOccurrences.setSelected(true);
      this.$$$loadButtonText$$$(cbSearchTextOccurrences,
                                this.$$$getMessageFromBundle$$$("messages/KotlinBundle", "search.for.text.occurrences"));
      panel4.add(cbSearchTextOccurrences,
                 new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      cbSearchReferences = new JCheckBox();
      cbSearchReferences.setSelected(true);
      this.$$$loadButtonText$$$(cbSearchReferences,
                                this.$$$getMessageFromBundle$$$("messages/KotlinBundle", "checkbox.text.search.references"));
      panel4.add(cbSearchReferences, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      cbDeleteEmptySourceFiles = new JCheckBox();
      cbDeleteEmptySourceFiles.setSelected(true);
      this.$$$loadButtonText$$$(cbDeleteEmptySourceFiles,
                                this.$$$getMessageFromBundle$$$("messages/KotlinBundle", "checkbox.text.delete.empty.source.files"));
      panel4.add(cbDeleteEmptySourceFiles, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      cbApplyMPPDeclarationsMove = new JCheckBox();
      cbApplyMPPDeclarationsMove.setSelected(true);
      this.$$$loadButtonText$$$(cbApplyMPPDeclarationsMove,
                                this.$$$getMessageFromBundle$$$("messages/KotlinBundle", "label.text.move.expect.actual.counterparts"));
      panel4.add(cbApplyMPPDeclarationsMove, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
      final Spacer spacer1 = new Spacer();
      panel2.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                              GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      label1.setLabelFor(tfFileNameInPackage);
      label2.setLabelFor(destinationFolderCB);
      ButtonGroup buttonGroup;
      buttonGroup = new ButtonGroup();
      buttonGroup.add(rbMoveToPackage);
      buttonGroup.add(rbMoveToFile);
    }

    init();

    List<KtFile> sourceFiles = getSourceFiles(elementsToMove);

    this.moveCallback = moveCallback;
    this.initialTargetDirectory = targetDirectory;


    setTitle(MoveHandler.getRefactoringName());

    List<KtNamedDeclaration> allDeclarations = getAllDeclarations(sourceFiles);

    initSearchOptions(searchInComments, searchForTextOccurrences, deleteEmptySourceFiles, moveMppDeclarations, allDeclarations);

    initPackageChooser(targetPackageName, targetDirectory, sourceFiles);

    initFileChooser(targetFile, freezeTargets ? targetDirectory : null, elementsToMove, sourceFiles);

    initMoveToButtons(moveToPackage);

    initMemberInfo(elementsToMove, allDeclarations);

    updateControls();

    initializedCheckBoxesState = getCheckboxesState(true);
  }

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return mainPanel; }

  private final BitSet initializedCheckBoxesState;

  private BitSet getCheckboxesState(boolean applyDefaults) {

    BitSet state = new BitSet(5);

    state.set(0, applyDefaults || cbSearchInComments.isSelected()); //cbSearchInComments default is true
    state.set(1, applyDefaults || cbSearchTextOccurrences.isSelected()); //cbSearchTextOccurrences default is true
    state.set(2, applyDefaults || cbDeleteEmptySourceFiles.isSelected()); //cbDeleteEmptySourceFiles default is true
    state.set(3, applyDefaults || cbApplyMPPDeclarationsMove.isSelected()); //cbApplyMPPDeclarationsMove default is true
    state.set(4, cbSearchReferences.isSelected());

    return state;
  }

  private static List<KtFile> getSourceFiles(@NotNull Collection<KtNamedDeclaration> elementsToMove) {
    return CollectionsKt.distinct(
      CollectionsKt.map(
        elementsToMove,
        KtPureElement::getContainingKtFile
      )
    );
  }

  private static List<KtNamedDeclaration> getAllDeclarations(Collection<KtFile> sourceFiles) {
    return CollectionsKt.filterIsInstance(
      CollectionsKt.flatMap(
        sourceFiles,
        KtPsiUtilKt::getFileOrScriptDeclarations
      ),
      KtNamedDeclaration.class
    );
  }

  private void initMemberInfo(
    @NotNull Set<KtNamedDeclaration> elementsToMove,
    @NotNull List<KtNamedDeclaration> declarations
  ) {
    //KotlinMemberInfo run resolve on declaration so it is good to place it to the process
    List<KotlinMemberInfo> memberInfos =
      MoveUtilKt.mapWithReadActionInProcess(declarations, myProject, MoveHandler.getRefactoringName(), (declaration) -> {
        KotlinMemberInfo memberInfo = new KotlinMemberInfo(declaration, false);
        memberInfo.setChecked(elementsToMove.contains(declaration));
        return memberInfo;
      });

    KotlinMemberSelectionPanel selectionPanel = new KotlinMemberSelectionPanel(getTitle(), memberInfos, null);
    memberTable = selectionPanel.getTable();
    MemberInfoModelImpl memberInfoModel = new MemberInfoModelImpl();
    memberInfoModel.memberInfoChanged(new MemberInfoChange<>(memberInfos));
    selectionPanel.getTable().setMemberInfoModel(memberInfoModel);
    selectionPanel.getTable().addMemberInfoChangeListener(memberInfoModel);
    selectionPanel.getTable().addMemberInfoChangeListener(listener -> updateControls());
    cbApplyMPPDeclarationsMove.addChangeListener(e -> updateControls());
    memberInfoPanel.add(selectionPanel, BorderLayout.CENTER);
  }

  private void updateSuggestedFileName() {
    tfFileNameInPackage.setText(MoveUtilKt.guessNewFileName(getSelectedElementsToMove()));
  }

  private void updateFileNameInPackageField() {
    boolean movingSingleFileToPackage = rbMoveToPackage.isSelected() && getSourceFiles(getSelectedElementsToMove()).size() == 1;
    tfFileNameInPackage.setEnabled(movingSingleFileToPackage);
  }

  private void initPackageChooser(
    String targetPackageName,
    PsiDirectory targetDirectory,
    List<KtFile> sourceFiles
  ) {
    if (targetPackageName != null) {
      classPackageChooser.prependItem(targetPackageName);
      classPackageChooser.setEnabled(freezeTargets);
    }

    ((KotlinDestinationFolderComboBox)destinationFolderCB).setData(
      myProject,
      targetDirectory,
      s -> setErrorText(s),
      classPackageChooser.getChildComponent()
    );
  }

  private void initSearchOptions(
    boolean searchInComments,
    boolean searchForTextOccurences,
    boolean deleteEmptySourceFiles,
    boolean moveMppDeclarations,
    List<KtNamedDeclaration> allDeclarations
  ) {
    cbSearchInComments.setSelected(searchInComments);
    cbSearchTextOccurrences.setSelected(searchForTextOccurences);
    cbDeleteEmptySourceFiles.setSelected(deleteEmptySourceFiles);
    cbApplyMPPDeclarationsMove.setSelected(moveMppDeclarations);
    cbApplyMPPDeclarationsMove.setVisible(isMPPDeclarationInList(allDeclarations));
  }

  private void initMoveToButtons(boolean moveToPackage) {
    if (moveToPackage) {
      rbMoveToPackage.setSelected(true);
    }
    else {
      rbMoveToFile.setSelected(true);
    }

    rbMoveToPackage.addActionListener(
      e -> {
        classPackageChooser.requestFocus();
        updateControls();
      }
    );

    rbMoveToFile.addActionListener(
      e -> {
        fileChooser.requestFocus();
        updateControls();
      }
    );
  }

  private void initFileChooser(
    @Nullable KtFile targetFile,
    @Nullable PsiDirectory targetDirectory,
    @NotNull Set<KtNamedDeclaration> elementsToMove,
    @NotNull List<KtFile> sourceFiles
  ) {
    PsiDirectory sourceDir = sourceFiles.get(0).getParent();
    if (sourceDir == null) {
      throw new AssertionError("File chooser initialization failed");
    }

    Module targetModule = (targetDirectory != null) ? ModuleUtilCore.findModuleForPsiElement(targetDirectory) : null;
    GlobalSearchScope targetModuleScope = targetModule == null ? null
                                                               : GlobalSearchScope.getScopeRestrictedByFileTypes(
                                                                 targetModule.getModuleScope(), KotlinFileType.INSTANCE);

    fileChooser.addActionListener(e -> {
                                    KotlinFileChooserDialog dialog = new KotlinFileChooserDialog(
                                      KotlinBundle.message("text.choose.containing.file"),
                                      myProject,
                                      targetModuleScope, freezeTargets ? null : getTargetPackage());

                                    File targetFile1 = new File(fileChooser.getText());
                                    PsiFile targetPsiFile = PhysicalFileSystemUtilsKt.toPsiFile(targetFile1, myProject);

                                    if (targetPsiFile != null) {
                                      if (targetPsiFile instanceof KtFile) {
                                        dialog.select((KtFile)targetPsiFile);
                                      }
                                      else {
                                        PsiDirectory targetDir = PhysicalFileSystemUtilsKt.toPsiDirectory(targetFile1.getParentFile(), myProject);
                                        if (targetDir != null) {
                                          dialog.selectDirectory(targetDir);
                                        }
                                        else {
                                          dialog.selectDirectory(sourceDir);
                                        }
                                      }
                                    }
                                    else {
                                      dialog.selectDirectory(sourceDir);
                                    }

                                    dialog.showDialog();
                                    KtFile selectedFile = dialog.isOK() ? dialog.getSelected() : null;
                                    if (selectedFile != null) {
                                      fileChooser.setText(selectedFile.getVirtualFile().getPath());
                                    }
                                  }
    );

    String initialTargetPath = targetFile != null
                               ? targetFile.getVirtualFile().getPath()
                               : sourceFiles.get(0).getVirtualFile().getParent().getPath() +
                                 "/" +
                                 MoveUtilKt.guessNewFileName(elementsToMove);
    fileChooser.setText(initialTargetPath);
  }

  private ReferenceEditorComboWithBrowseButton createPackageChooser() {
    return new PackageNameReferenceEditorCombo(
      "",
      myProject,
      RECENTS_KEY,
      RefactoringBundle.message("choose.destination.package")
    );
  }

  private static boolean isMPPDeclarationInList(List<KtNamedDeclaration> declarations) {
    for (KtNamedDeclaration element : declarations) {
      if (ExpectActualUtilKt.isEffectivelyActual(element, true) ||
          ExpectActualUtilKt.isExpectDeclaration(element)) {
        return true;
      }
    }
    return false;
  }

  private boolean isMppDeclarationSelected() {
    return isMPPDeclarationInList(getSelectedElementsToMove());
  }

  private void updateControls() {

    boolean mppDeclarationSelected = isMppDeclarationSelected();
    cbApplyMPPDeclarationsMove.setEnabled(mppDeclarationSelected);

    boolean needToMoveMPPDeclarations = mppDeclarationSelected && cbApplyMPPDeclarationsMove.isSelected();

    if (needToMoveMPPDeclarations) {
      if (!rbMoveToPackage.isSelected()) {
        rbMoveToPackage.setSelected(true);
      }
    }
    UIUtil.setEnabled(rbMoveToFile, !needToMoveMPPDeclarations, true);

    boolean moveToPackage = rbMoveToPackage.isSelected();
    classPackageChooser.setEnabled(moveToPackage && freezeTargets);
    updateFileNameInPackageField();
    fileChooser.setEnabled(!moveToPackage);
    UIUtil.setEnabled(targetPanel, moveToPackage && !needToMoveMPPDeclarations && hasAnySourceRoots(), true);
    updateSuggestedFileName();
    myHelpAction.setEnabled(false);
  }

  private boolean hasAnySourceRoots() {
    return !getSuitableDestinationSourceRoots(myProject).isEmpty();
  }

  private void saveRefactoringSettings() {
    KotlinRefactoringSettings refactoringSettings = KotlinRefactoringSettings.getInstance();
    refactoringSettings.MOVE_SEARCH_IN_COMMENTS = cbSearchInComments.isSelected();
    refactoringSettings.MOVE_SEARCH_FOR_TEXT = cbSearchTextOccurrences.isSelected();
    refactoringSettings.MOVE_DELETE_EMPTY_SOURCE_FILES = cbDeleteEmptySourceFiles.isSelected();
    refactoringSettings.MOVE_PREVIEW_USAGES = isPreviewUsages();
    refactoringSettings.MOVE_MPP_DECLARATIONS = cbApplyMPPDeclarationsMove.isSelected();

    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, getTargetPackage());
  }

  private List<KtNamedDeclaration> getSelectedElementsToMove() {
    return CollectionsKt.map(
      memberTable.getSelectedMemberInfos(),
      MemberInfoBase::getMember
    );
  }

  @Override
  protected JComponent createCenterPanel() {
    return mainPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#" + getClass().getName();
  }

  private String getTargetPackage() {
    return classPackageChooser.getText().trim();
  }

  private List<KtNamedDeclaration> getSelectedElementsToMoveChecked() throws ConfigurationException {
    List<KtNamedDeclaration> elementsToMove = getSelectedElementsToMove();
    if (elementsToMove.isEmpty()) {
      throw new ConfigurationException(KotlinBundle.message("text.no.elements.to.move.are.selected"));
    }
    return elementsToMove;
  }

  private MoveKotlinTopLevelDeclarationsModel getModel() throws ConfigurationException {

    boolean mppDeclarationSelected = cbApplyMPPDeclarationsMove.isSelected() && isMppDeclarationSelected();

    PsiDirectory selectedPsiDirectory = null;
    if (!mppDeclarationSelected) {
      DirectoryChooser.ItemWrapper selectedItem = (DirectoryChooser.ItemWrapper)destinationFolderCB.getComboBox().getSelectedItem();
      selectedPsiDirectory = selectedItem != null ? selectedItem.getDirectory() : initialTargetDirectory;
    }

    List<KtNamedDeclaration> selectedElements = getSelectedElementsToMoveChecked();

    return new MoveKotlinTopLevelDeclarationsModel(
      myProject,
      selectedElements,
      getTargetPackage(),
      selectedPsiDirectory,
      tfFileNameInPackage.getText(),
      fileChooser.getText(),
      rbMoveToPackage.isSelected(),
      cbSearchReferences.isSelected(),
      cbSearchInComments.isSelected(),
      cbSearchTextOccurrences.isSelected(),
      cbDeleteEmptySourceFiles.isSelected(),
      mppDeclarationSelected,
      moveCallback
    );
  }

  @Override
  protected void doAction() {

    ModelResultWithFUSData modelResult;
    try {
      modelResult = getModel().computeModelResult();
    }
    catch (ConfigurationException e) {
      setErrorHtml(e.getMessageHtml());
      return;
    }

    saveRefactoringSettings();

    try {
      MoveUtilKt.logFusForMoveRefactoring(
        modelResult.getElementsCount(),
        modelResult.getEntityToMove(),
        modelResult.getDestination(),
        getCheckboxesState(false).equals(initializedCheckBoxesState),
        () -> invokeRefactoring(modelResult.getProcessor())
      );
    }
    catch (IncorrectOperationException e) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.getMessage(), null, myProject);
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return classPackageChooser.getChildComponent();
  }

  private static class MemberInfoModelImpl extends AbstractMemberInfoModel<KtNamedDeclaration, KotlinMemberInfo> {
  }
}
