// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.psiViewer;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.documentation.render.DocRenderManager;
import com.intellij.dev.psiViewer.formatter.BlockViewerPsiBasedTree;
import com.intellij.dev.psiViewer.stubs.StubViewerPsiBasedTree;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.tabs.JBEditorTabsBase;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.JBTabsFactory;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

/**
 * @author Konstantin Bulenkov
 */
public class PsiViewerDialog extends DialogWrapper implements DataProvider {
  private static final Color BOX_COLOR = new JBColor(new Color(0xFC6C00), new Color(0xDE6C01));
  public static final Logger LOG = Logger.getInstance(PsiViewerDialog.class);
  private final Project myProject;
  private final StructureTreeModel<ViewerTreeStructure> myStructureTreeModel;
  private final ViewerTreeStructure myTreeStructure;

  private JPanel myPanel;
  private JComboBox<PsiViewerSourceWrapper> myFileTypeComboBox;
  private JCheckBox myShowWhiteSpacesBox;
  private JCheckBox myShowTreeNodesCheckBox;
  private JBLabel myDialectLabel;
  private JComboBox<Language> myDialectComboBox;
  private JLabel myExtensionLabel;
  private JComboBox<String> myExtensionComboBox;
  private JPanel myTextPanel;
  private JSplitPane myTextSplit;
  private JSplitPane myTreeSplit;
  private Tree myPsiTree;
  private final JList<String> myRefs;

  private TitledSeparator myTextSeparator;
  private TitledSeparator myPsiTreeSeparator;

  @NotNull
  private final StubViewerPsiBasedTree myStubTree;

  @NotNull
  private final BlockViewerPsiBasedTree myBlockTree;
  private RangeHighlighter myHighlighter;


  private final Set<PsiViewerSourceWrapper> mySourceWrappers = new TreeSet<>();
  private final EditorEx myEditor;
  private final EditorListener myEditorListener = new EditorListener();
  private String myLastParsedText;
  private int myLastParsedTextHashCode = 17;
  private int myNewDocumentHashCode = 11;

  private final boolean myExternalDocument;

  private final Map<PsiElement, PsiElement[]> myRefsResolvedCache = new HashMap<>();

  private final PsiFile myOriginalPsiFile;

  @NotNull
  private final JBTabs myTabs;

  private void createUIComponents() {
    myPsiTree = new Tree();
  }


  private static class ExtensionComparator implements Comparator<String> {
    private final String myOnTop;

    ExtensionComparator(String onTop) {
      myOnTop = onTop;
    }

    @Override
    public int compare(@NotNull String o1, @NotNull String o2) {
      if (o1.equals(myOnTop)) return -1;
      if (o2.equals(myOnTop)) return 1;
      return o1.compareToIgnoreCase(o2);
    }
  }

  PsiViewerDialog(@NotNull Project project, @Nullable Editor selectedEditor) {
    super(project, true, IdeModalityType.MODELESS);
    myProject = project;
    myExternalDocument = selectedEditor != null;
    myOriginalPsiFile = getOriginalPsiFile(project, selectedEditor);
    myTabs = createTabPanel(project);
    myRefs = new JBList<>(new DefaultListModel<>());

    myTreeStructure = new ViewerTreeStructure(myProject);
    myStructureTreeModel = new StructureTreeModel<>(myTreeStructure, IndexComparator.INSTANCE, getDisposable());
    AsyncTreeModel asyncTreeModel = new AsyncTreeModel(myStructureTreeModel, getDisposable());
    myPsiTree.setModel(asyncTreeModel);

    ViewerPsiBasedTree.PsiTreeUpdater psiTreeUpdater = new ViewerPsiBasedTree.PsiTreeUpdater() {

      private final TextAttributes myAttributes;

      {
        myAttributes = new TextAttributes();
        myAttributes.setEffectColor(BOX_COLOR);
        myAttributes.setEffectType(EffectType.ROUNDED_BOX);
      }

      @Override
      public void updatePsiTree(@NotNull PsiElement toSelect, @Nullable TextRange selectRangeInEditor) {
        if (selectRangeInEditor != null) {
          int start = selectRangeInEditor.getStartOffset();
          int end = selectRangeInEditor.getEndOffset();
          clearSelection();
          if (end <= myEditor.getDocument().getTextLength()) {
            myHighlighter = myEditor.getMarkupModel()
              .addRangeHighlighter(start, end, HighlighterLayer.LAST, myAttributes, HighlighterTargetArea.EXACT_RANGE);

            myEditor.getCaretModel().moveToOffset(start);
            myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
          }
        }
        updateReferences(toSelect);
        if (!myPsiTree.hasFocus()) {
          selectElement(toSelect);
        }
      }
    };
    myStubTree = new StubViewerPsiBasedTree(project, psiTreeUpdater);
    myBlockTree = new BlockViewerPsiBasedTree(project, psiTreeUpdater);
    Disposer.register(getDisposable(), myStubTree);
    Disposer.register(getDisposable(), myBlockTree);

    setOKButtonText(DevPsiViewerBundle.message("button.build.psi.tree"));
    setCancelButtonText(DevPsiViewerBundle.message("button.close"));
    Disposer.register(myProject, getDisposable());
    VirtualFile selectedFile = selectedEditor == null ? null : FileDocumentManager.getInstance().getFile(selectedEditor.getDocument());
    setTitle(selectedFile == null ? 
             DevPsiViewerBundle.message("dialog.title.psi.viewer") : 
             DevPsiViewerBundle.message("dialog.title.psi.viewer.with.file", selectedFile.getName()));
    if (selectedEditor != null) {
      myEditor = (EditorEx)EditorFactory.getInstance().createEditor(selectedEditor.getDocument(), myProject);
    }
    else {
      PsiViewerSettings settings = PsiViewerSettings.getSettings();
      Document document = EditorFactory.getInstance().createDocument(StringUtil.notNullize(settings.text));
      myEditor = (EditorEx)EditorFactory.getInstance().createEditor(document, myProject);
      myEditor.getSelectionModel().setSelection(0, document.getTextLength());
    }
    myEditor.getSettings().setLineMarkerAreaShown(false);
    DocRenderManager.setDocRenderingEnabled(myEditor, false);
    init();
    if (selectedEditor != null) {
      doOKAction();

      ApplicationManager.getApplication().invokeLater(() -> {
        getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(myEditor.getContentComponent(), true));
        myEditor.getCaretModel().moveToOffset(selectedEditor.getCaretModel().getOffset());
        myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }, ModalityState.stateForComponent(myPanel));
    }
  }

  private static @Nullable PsiFile getOriginalPsiFile(@NotNull Project project, @Nullable Editor selectedEditor) {
    return selectedEditor != null ? PsiDocumentManager.getInstance(project).getPsiFile(selectedEditor.getDocument()) : null;
  }

  @NotNull
  private JBTabs createTabPanel(@NotNull Project project) {
    JBEditorTabsBase tabs = JBTabsFactory.createEditorTabs(project, getDisposable());
    tabs.getPresentation().setAlphabeticalMode(false).setSupportsCompression(false);
    return tabs;
  }

  @Override
  protected void init() {
    initMnemonics();

    initTree(myPsiTree);
    TreeCellRenderer renderer = myPsiTree.getCellRenderer();
    myPsiTree.setCellRenderer((tree, value, selected, expanded, leaf, row, hasFocus) -> {
      Component c = renderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      if (value instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (userObject instanceof ViewerNodeDescriptor descriptor) {
          Object element = descriptor.getElement();
          if (c instanceof NodeRenderer nodeRenderer) {
            nodeRenderer.setToolTipText(getElementDescription(element));
          }
          if (element instanceof PsiElement psiElement && FileContextUtil.getFileContext(psiElement.getContainingFile()) != null ||
              element instanceof ViewerTreeStructure.Inject) {
            TextAttributes attr =
              EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.INJECTED_LANGUAGE_FRAGMENT);
            c.setBackground(attr.getBackgroundColor());
          }
        }
      }
      return c;
    });
    myPsiTree.addTreeSelectionListener(new MyPsiTreeSelectionListener());

    JPanel panelWrapper = new JPanel(new BorderLayout());
    panelWrapper.add(myTabs.getComponent());
    myTreeSplit.add(panelWrapper, JSplitPane.RIGHT);

    JPanel referencesPanel = new JPanel(new BorderLayout());
    referencesPanel.add(myRefs);
    referencesPanel.setBorder(IdeBorderFactory.createBorder());

    myTabs.addTab(new TabInfo(referencesPanel).setText(DevPsiViewerBundle.message("tab.title.references")));
    myTabs.addTab(new TabInfo(myBlockTree.getComponent()).setText(DevPsiViewerBundle.message("tab.title.block.structure")));
    myTabs.addTab(new TabInfo(myStubTree.getComponent()).setText(DevPsiViewerBundle.message("tab.title.stub.structure")));
    PsiViewerSettings settings = PsiViewerSettings.getSettings();
    int tabIndex = settings.lastSelectedTabIndex;
    TabInfo defaultInfo = tabIndex < myTabs.getTabCount() ? myTabs.getTabAt(tabIndex) : null;
    if (defaultInfo != null) {
      myTabs.select(defaultInfo, false);
    }
    myTabs.setSelectionChangeHandler((tab, focus, el) -> {
      settings.lastSelectedTabIndex = myTabs.getIndexOf(tab);
      return el.run();
    });

    GoToListener listener = new GoToListener();
    myRefs.addKeyListener(listener);
    myRefs.addMouseListener(listener);
    myRefs.getSelectionModel().addListSelectionListener(listener);
    myRefs.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(@NotNull JList list,
                                                    Object value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        PsiElement[] elements = myRefsResolvedCache.get(getPsiElement());
        if (elements == null || elements.length <= index || elements[index] == null) {
          comp.setForeground(JBColor.RED);
        }
        return comp;
      }
    });

    myEditor.getSettings().setFoldingOutlineShown(false);
    myEditor.getDocument().addDocumentListener(myEditorListener, getDisposable());
    myEditor.getSelectionModel().addSelectionListener(myEditorListener);
    myEditor.getCaretModel().addCaretListener(myEditorListener);

    FocusTraversalPolicy oldPolicy = getPeer().getWindow().getFocusTraversalPolicy();
    getPeer().getWindow().setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
      @Override
      public Component getInitialComponent(@NotNull Window window) {
        return myEditor.getComponent();
      }
    });
    Disposer.register(getDisposable(), () -> getPeer().getWindow().setFocusTraversalPolicy(oldPolicy));
    VirtualFile file = myExternalDocument ? FileDocumentManager.getInstance().getFile(myEditor.getDocument()) : null;
    Language curLanguage = LanguageUtil.getLanguageForPsi(myProject, file);

    String type = curLanguage != null ? curLanguage.getDisplayName() : settings.type;
    PsiViewerSourceWrapper lastUsed = null;
    mySourceWrappers.addAll(PsiViewerSourceWrapper.getExtensionBasedWrappers());

    List<PsiViewerSourceWrapper> fileTypeBasedWrappers = PsiViewerSourceWrapper.getFileTypeBasedWrappers();
    for (PsiViewerSourceWrapper wrapper : fileTypeBasedWrappers) {
      mySourceWrappers.addAll(fileTypeBasedWrappers);
      if (lastUsed == null && wrapper.getText().equals(type) ||
          curLanguage != null && wrapper.myFileType == curLanguage.getAssociatedFileType()) {
        lastUsed = wrapper;
      }
    }

    myFileTypeComboBox.setModel(new CollectionComboBoxModel<>(new ArrayList<>(mySourceWrappers), lastUsed));
    myFileTypeComboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      if (value != null) {
        label.setText(value.getText());
        label.setIcon(value.getIcon());
      }
    }));
    ComboboxSpeedSearch search1 = new ComboboxSpeedSearch(myFileTypeComboBox, null) {
      @Override
      protected String getElementText(Object element) {
        return element instanceof PsiViewerSourceWrapper ? ((PsiViewerSourceWrapper)element).getText() : null;
      }
    };
    search1.setupListeners();
    myFileTypeComboBox.addActionListener(__ -> {
      updateDialectsCombo(null);
      updateExtensionsCombo();
      updateEditor();
    });
    myDialectComboBox.addActionListener(__ -> updateEditor());
    ComboboxSpeedSearch search = new ComboboxSpeedSearch(myDialectComboBox, null) {
      @Override
      protected String getElementText(Object element) {
        return element instanceof Language ? ((Language)element).getDisplayName() : "<default>";
      }
    };
    search.setupListeners();
    myFileTypeComboBox.addFocusListener(new AutoExpandFocusListener(myFileTypeComboBox));
    if (!myExternalDocument && lastUsed == null && !mySourceWrappers.isEmpty()) {
      myFileTypeComboBox.setSelectedIndex(0);
    }

    myDialectComboBox.setRenderer(SimpleListCellRenderer.create(DevPsiViewerBundle.message("label.none"), value -> value.getDisplayName()));
    myDialectComboBox.addFocusListener(new AutoExpandFocusListener(myDialectComboBox));
    myExtensionComboBox.setRenderer(SimpleListCellRenderer.create("", value -> "." + value)); //NON-NLS
    myExtensionComboBox.addFocusListener(new AutoExpandFocusListener(myExtensionComboBox));

    myShowWhiteSpacesBox.addActionListener(__ -> {
      myTreeStructure.setShowWhiteSpaces(myShowWhiteSpacesBox.isSelected());
      myStructureTreeModel.invalidateAsync();
    });
    myShowTreeNodesCheckBox.addActionListener(__ -> {
      myTreeStructure.setShowTreeNodes(myShowTreeNodesCheckBox.isSelected());
      myStructureTreeModel.invalidateAsync();
    });
    myShowWhiteSpacesBox.setSelected(settings.showWhiteSpaces);
    myTreeStructure.setShowWhiteSpaces(settings.showWhiteSpaces);
    myShowTreeNodesCheckBox.setSelected(settings.showTreeNodes);
    myTreeStructure.setShowTreeNodes(settings.showTreeNodes);
    myTextPanel.setLayout(new BorderLayout());
    myTextPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    updateDialectsCombo(settings.dialect);
    updateExtensionsCombo();

    registerCustomKeyboardActions();

    Dimension size = DimensionService.getInstance().getSize(getDimensionServiceKey(), myProject);
    if (size == null) {
      DimensionService.getInstance().setSize(getDimensionServiceKey(), JBUI.size(800, 600), myProject);
    }
    myTextSplit.setDividerLocation(settings.textDividerLocation);
    myTreeSplit.setDividerLocation(settings.treeDividerLocation);

    updateEditor();
    super.init();
  }

  @NotNull
  private static @NlsSafe String getElementDescription(Object element) {
    return element.getClass().getName();
  }

  public static void initTree(JTree tree) {
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.updateUI();
    ToolTipManager.sharedInstance().registerComponent(tree);
    TreeUtil.installActions(tree);
    TreeUIHelper.getInstance().installTreeSpeedSearch(tree);
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return "#com.intellij.internal.psiView.PsiViewerDialog";
  }

  @Override
  protected String getHelpId() {
    return "reference.psi.viewer";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditor.getContentComponent();
  }

  private void registerCustomKeyboardActions() {
    int mask = SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.ALT_DOWN_MASK;

    registerKeyboardAction(__ -> focusEditor(), KeyStroke.getKeyStroke(KeyEvent.VK_T, mask));

    registerKeyboardAction(__ -> focusTree(), KeyStroke.getKeyStroke(KeyEvent.VK_S, mask));

    registerKeyboardAction(__ -> myBlockTree.focusTree(), KeyStroke.getKeyStroke(KeyEvent.VK_K, mask));

    registerKeyboardAction(__ -> focusRefs(), KeyStroke.getKeyStroke(KeyEvent.VK_R, mask));

    registerKeyboardAction(__ -> {
      if (myRefs.isFocusOwner()) {
        myBlockTree.focusTree();
      }
      else if (myPsiTree.isFocusOwner()) {
        focusRefs();
      }
      else if (myBlockTree.isFocusOwner()) {
        focusTree();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
  }

  private void registerKeyboardAction(ActionListener actionListener, KeyStroke keyStroke) {
    getRootPane().registerKeyboardAction(actionListener, keyStroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private void focusEditor() {
    IdeFocusManager.getInstance(myProject).requestFocus(myEditor.getContentComponent(), true);
  }

  private void focusTree() {
    IdeFocusManager.getInstance(myProject).requestFocus(myPsiTree, true);
  }

  private void focusRefs() {
    IdeFocusManager.getInstance(myProject).requestFocus(myRefs, true);
    if (myRefs.getModel().getSize() > 0) {
      if (myRefs.getSelectedIndex() == -1) {
        myRefs.setSelectedIndex(0);
      }
    }
  }

  private void initMnemonics() {
    myTextSeparator.setLabelFor(myEditor.getContentComponent());
    myPsiTreeSeparator.setLabelFor(myPsiTree);
  }

  @Nullable
  private PsiElement getPsiElement() {
    TreePath path = myPsiTree.getSelectionPath();
    return path == null ? null : getPsiElement((DefaultMutableTreeNode)path.getLastPathComponent());
  }

  @Nullable
  private static PsiElement getPsiElement(DefaultMutableTreeNode node) {
    if (node.getUserObject() instanceof ViewerNodeDescriptor descriptor) {
      Object elementObject = descriptor.getElement();
      return elementObject instanceof PsiElement
             ? (PsiElement)elementObject
             : elementObject instanceof ASTNode ? ((ASTNode)elementObject).getPsi() : null;
    }
    return null;
  }

  private void updateDialectsCombo(@Nullable String lastUsed) {
    Object source = getSource();
    ArrayList<Language> items = new ArrayList<>();
    if (source instanceof LanguageFileType) {
      Language baseLang = ((LanguageFileType)source).getLanguage();
      JBTreeTraverser.from(Language::getDialects).withRoot(baseLang)
        .preOrderDfsTraversal()
        .addAllTo(items);
      items.subList(1, items.size()).sort(LanguageUtil.LANGUAGE_COMPARATOR);
    }
    myDialectComboBox.setModel(new CollectionComboBoxModel<>(items));

    boolean visible = items.size() > 1;
    myDialectLabel.setVisible(visible);
    myDialectComboBox.setVisible(visible);
    if (visible && (myExternalDocument || lastUsed != null)) {
      VirtualFile file = myExternalDocument ? FileDocumentManager.getInstance().getFile(myEditor.getDocument()) : null;
      Language curLanguage = LanguageUtil.getLanguageForPsi(myProject, file);
      int idx = items.indexOf(curLanguage);
      myDialectComboBox.setSelectedIndex(Math.max(idx, 0));
    }
  }

  private void updateExtensionsCombo() {
    Object source = getSource();
    if (source instanceof LanguageFileType) {
      List<String> extensions = getAllExtensions((LanguageFileType)source);
      if (extensions.size() > 1) {
        ExtensionComparator comp = new ExtensionComparator(extensions.get(0));
        extensions.sort(comp);
        SortedComboBoxModel<String> model = new SortedComboBoxModel<>(comp);
        model.setAll(extensions);
        myExtensionComboBox.setModel(model);
        myExtensionComboBox.setVisible(true);
        myExtensionLabel.setVisible(true);
        VirtualFile file = myExternalDocument ? FileDocumentManager.getInstance().getFile(myEditor.getDocument()) : null;
        String fileExt = file == null ? "" : FileUtilRt.getExtension(file.getName()); //NON-NLS
        if (!fileExt.isEmpty() && extensions.contains(fileExt)) {
          myExtensionComboBox.setSelectedItem(fileExt);
          return;
        }
        myExtensionComboBox.setSelectedIndex(0);
        return;
      }
    }
    myExtensionComboBox.setVisible(false);
    myExtensionLabel.setVisible(false);
  }

  private static final Pattern EXT_PATTERN = Pattern.compile("[a-z\\d]*");

  private static List<String> getAllExtensions(LanguageFileType fileType) {
    List<FileNameMatcher> associations = FileTypeManager.getInstance().getAssociations(fileType);
    List<String> extensions = new ArrayList<>();
    extensions.add(StringUtil.toLowerCase(fileType.getDefaultExtension()));
    for (FileNameMatcher matcher : associations) {
      String presentableString = StringUtil.toLowerCase(matcher.getPresentableString());
      if (presentableString.startsWith("*.")) {
        String ext = presentableString.substring(2);
        if (!ext.isEmpty() && !extensions.contains(ext) && EXT_PATTERN.matcher(ext).matches()) {
          extensions.add(ext);
        }
      }
    }
    return extensions;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  private Object getSource() {
    PsiViewerSourceWrapper wrapper = (PsiViewerSourceWrapper)myFileTypeComboBox.getSelectedItem();
    if (wrapper != null) {
      return wrapper.myFileType != null ? wrapper.myFileType : wrapper.myExtension;
    }
    return null;
  }

  @Override
  protected Action @NotNull [] createActions() {
    AbstractAction copyPsi = new AbstractAction(DevPsiViewerBundle.message("cop.y.psi")) {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        PsiElement element = parseText(myEditor.getDocument().getText());
        setOriginalFiles(element);
        List<PsiElement> allToParse = new ArrayList<>();
        if (element instanceof PsiFile) {
          allToParse.addAll(((PsiFile)element).getViewProvider().getAllFiles());
        }
        else if (element != null) {
          allToParse.add(element);
        }
        StringBuilder data = new StringBuilder();
        for (PsiElement psiElement : allToParse) {
          data.append(DebugUtil.psiToString(psiElement, myShowWhiteSpacesBox.isSelected(), true));
        }
        CopyPasteManager.getInstance().setContents(new StringSelection(data.toString()));
      }
    };
    return ArrayUtil.mergeArrays(new Action[]{copyPsi}, super.createActions());
  }

  @Override
  protected void doOKAction() {

    String text = myEditor.getDocument().getText();
    myEditor.getSelectionModel().removeSelection();

    myLastParsedText = text;
    myLastParsedTextHashCode = text.hashCode();
    myNewDocumentHashCode = myLastParsedTextHashCode;
    PsiElement rootElement = parseText(text);
    setOriginalFiles(rootElement);
    focusTree();
    myTreeStructure.setRootPsiElement(rootElement);

    myStructureTreeModel.invalidateAsync();
    myPsiTree.setRootVisible(true);
    myPsiTree.expandRow(0);
    myPsiTree.setRootVisible(false);


    myBlockTree.reloadTree(rootElement, text);
    myStubTree.reloadTree(rootElement, text);

    myRefsResolvedCache.clear();
  }

  private PsiElement parseText(@NotNull String text) {
    Object source = getSource();
    try {
      if (source instanceof PsiViewerExtension) {
        return ((PsiViewerExtension)source).createElement(myProject, text);
      }
      if (source instanceof FileType type) {
        String ext = type.getDefaultExtension();
        if (myExtensionComboBox.isVisible() && myExtensionComboBox.getSelectedItem() != null) {
          ext = StringUtil.toLowerCase(myExtensionComboBox.getSelectedItem().toString());
        }
        if (type instanceof LanguageFileType) {
          Language dialect = (Language)myDialectComboBox.getSelectedItem();
          if (dialect != null) {
            return PsiFileFactory.getInstance(myProject).createFileFromText("Dummy." + ext, dialect, text);
          }
        }
        return PsiFileFactory.getInstance(myProject).createFileFromText("Dummy." + ext, type, text);
      }
    }
    catch (IncorrectOperationException e) {
      Messages.showMessageDialog(myProject, e.getMessage(), CommonBundle.message("title.error"), Messages.getErrorIcon());
    }
    return null;
  }

  private void setOriginalFiles(@Nullable PsiElement root) {
    if (root != null && myOriginalPsiFile != null) {
      PsiFile newPsiFile = root.getContainingFile();
      newPsiFile.putUserData(PsiFileFactory.ORIGINAL_FILE, myOriginalPsiFile);

      VirtualFile newVirtualFile = newPsiFile.getVirtualFile();
      if (newVirtualFile instanceof LightVirtualFile) {
        ((LightVirtualFile)newVirtualFile).setOriginalFile(myOriginalPsiFile.getVirtualFile());
      }
    }
  }


  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      return (DataProvider)slowId -> getSlowData(slowId);
    }
    return null;
  }

  @Nullable
  private PsiFile getSlowData(@NonNls String d) {
    if (CommonDataKeys.NAVIGATABLE.is(d)) {
      String fqn = null;
      if (myPsiTree.hasFocus()) {
        TreePath path = myPsiTree.getSelectionPath();
        if (path != null) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
          if (!(node.getUserObject() instanceof ViewerNodeDescriptor descriptor)) return null;
          Object elementObject = descriptor.getElement();
          PsiElement element = elementObject instanceof PsiElement
                                     ? (PsiElement)elementObject
                                     : elementObject instanceof ASTNode ? ((ASTNode)elementObject).getPsi() : null;
          if (element != null) {
            fqn = element.getClass().getName();
          }
        }
      }
      else if (myRefs.hasFocus()) {
        fqn = myRefs.getSelectedValue();
      }
      if (fqn != null) {
        return getContainingFileForClass(fqn);
      }
    }
    return null;
  }

  private class MyPsiTreeSelectionListener implements TreeSelectionListener {
    private final TextAttributes myAttributes;

    MyPsiTreeSelectionListener() {
      myAttributes = new TextAttributes();
      myAttributes.setEffectColor(BOX_COLOR);
      myAttributes.setEffectType(EffectType.ROUNDED_BOX);
    }

    @Override
    public void valueChanged(@NotNull TreeSelectionEvent e) {
      if (!myEditor.getDocument().getText().equals(myLastParsedText) || myBlockTree.isFocusOwner()) return;
      TreePath path = myPsiTree.getSelectionPath();
      clearSelection();
      if (path != null) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        if (!(node.getUserObject() instanceof ViewerNodeDescriptor descriptor)) return;
        Object elementObject = descriptor.getElement();
        PsiElement element = elementObject instanceof PsiElement
                                   ? (PsiElement)elementObject
                                   : elementObject instanceof ASTNode ? ((ASTNode)elementObject).getPsi() : null;
        if (element != null) {
          TextRange rangeInHostFile = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, element.getTextRange());
          int start = rangeInHostFile.getStartOffset();
          int end = rangeInHostFile.getEndOffset();
          PsiElement rootPsiElement = myTreeStructure.getRootPsiElement();
          if (rootPsiElement != null) {
            int baseOffset = rootPsiElement.getTextRange().getStartOffset();
            start -= baseOffset;
            end -= baseOffset;
          }
          int textLength = myEditor.getDocument().getTextLength();
          if (end <= textLength) {
            myHighlighter = myEditor.getMarkupModel()
              .addRangeHighlighter(start, end, HighlighterLayer.LAST, myAttributes, HighlighterTargetArea.EXACT_RANGE);
            if (myPsiTree.hasFocus()) {
              myEditor.getCaretModel().moveToOffset(start);
              myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
            }
          }

          if (myPsiTree.hasFocus()) {
            myBlockTree.selectNodeFromPsi(element);
            myStubTree.selectNodeFromPsi(element);
          }
          updateReferences(element);
        }
      }
    }
  }

  private void updateReferences(@NotNull PsiElement element) {
    DefaultListModel<String> model = (DefaultListModel<String>)myRefs.getModel();
    model.clear();

    String progressTitle = DevPsiViewerBundle.message("psi.viewer.progress.dialog.update.refs");
    Callable<List<PsiReference>> updater =
      () -> DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> doUpdateReferences(element));

    List<PsiReference> psiReferences = computeSlowOperationsSafeInBgThread(myProject, progressTitle, updater);

    for (PsiReference reference : psiReferences) {
      model.addElement(getElementDescription(reference));
    }
  }

  private @NotNull List<PsiReference> doUpdateReferences(@NotNull PsiElement element) {
    PsiReferenceService referenceService = PsiReferenceService.getService();
    List<PsiReference> psiReferences = referenceService.getReferences(element, PsiReferenceService.Hints.NO_HINTS);

    if (myRefsResolvedCache.containsKey(element)) return psiReferences;

    PsiElement[] cache = new PsiElement[psiReferences.size()];

    for (int i = 0; i < psiReferences.size(); i++) {
      PsiReference reference = psiReferences.get(i);

      PsiElement resolveResult;
      if (reference instanceof PsiPolyVariantReference) {
        ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(true);
        resolveResult = results.length == 0 ? null : results[0].getElement();
      }
      else {
        resolveResult = reference.resolve();
      }
      cache[i] = resolveResult;
    }
    myRefsResolvedCache.put(element, cache);

    return psiReferences;
  }

  private void clearSelection() {
    if (myHighlighter != null) {
      myEditor.getMarkupModel().removeHighlighter(myHighlighter);
      myHighlighter.dispose();
    }
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
    PsiViewerSettings settings = PsiViewerSettings.getSettings();
    PsiViewerSourceWrapper wrapper = (PsiViewerSourceWrapper)myFileTypeComboBox.getSelectedItem();
    if (wrapper != null) settings.type = wrapper.getText();
    if (!myExternalDocument) {
      settings.text = StringUtil.first(myEditor.getDocument().getText(), 2048, true);
    }
    settings.showTreeNodes = myShowTreeNodesCheckBox.isSelected();
    settings.showWhiteSpaces = myShowWhiteSpacesBox.isSelected();
    Object selectedDialect = myDialectComboBox.getSelectedItem();
    settings.dialect = myDialectComboBox.isVisible() && selectedDialect != null ? selectedDialect.toString() : "";
    settings.textDividerLocation = myTextSplit.getDividerLocation();
    settings.treeDividerLocation = myTreeSplit.getDividerLocation();
  }

  @Override
  public void dispose() {
    if (!myEditor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
    super.dispose();
  }

  @Nullable
  private PsiFile getContainingFileForClass(@NotNull String fqn) {
    String filename = fqn;
    if (fqn.contains(".")) {
      filename = fqn.substring(fqn.lastIndexOf('.') + 1);
    }
    if (filename.contains("$")) {
      filename = filename.substring(0, filename.indexOf('$'));
    }
    filename += ".java";
    PsiFile[] files = FilenameIndex.getFilesByName(myProject, filename, GlobalSearchScope.allScope(myProject));
    return ArrayUtil.getFirstElement(files);
  }

  private class GoToListener implements KeyListener, MouseListener, ListSelectionListener {
    private RangeHighlighter myListenerHighlighter;

    private void navigate() {
      String fqn = myRefs.getSelectedValue();
      PsiFile file = getContainingFileForClass(fqn);
      if (file != null) file.navigate(true);
    }

    @Override
    public void keyPressed(@NotNull KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        navigate();
      }
    }

    @Override
    public void mouseClicked(@NotNull MouseEvent e) {
      if (e.getClickCount() > 1) {
        navigate();
      }
    }

    @Override
    public void valueChanged(@NotNull ListSelectionEvent e) {
      clearSelection();
      updateDialectsCombo(null);
      updateExtensionsCombo();
      int ind = myRefs.getSelectedIndex();
      PsiElement element = getPsiElement();
      if (ind > -1 && element != null) {
        PsiReference[] references = element.getReferences();
        if (ind < references.length) {
          TextRange textRange = references[ind].getRangeInElement();
          TextRange range = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, element.getTextRange());
          int start = range.getStartOffset();
          PsiElement rootPsiElement = myTreeStructure.getRootPsiElement();
          if (rootPsiElement != null) {
            int baseOffset = rootPsiElement.getTextRange().getStartOffset();
            start -= baseOffset;
          }

          start += textRange.getStartOffset();
          int end = start + textRange.getLength();
          //todo[kb] probably move highlight color to the editor color scheme?
          TextAttributes highlightReferenceTextRange = new TextAttributes(null, null,
                                                                          JBColor.namedColor("PsiViewer.referenceHighlightColor", 0xA8C023),
                                                                          EffectType.BOLD_DOTTED_LINE, Font.PLAIN);
          myListenerHighlighter = myEditor.getMarkupModel()
            .addRangeHighlighter(start, end, HighlighterLayer.LAST,
                                 highlightReferenceTextRange, HighlighterTargetArea.EXACT_RANGE);
        }
      }
    }

    public void clearSelection() {
      if (myListenerHighlighter != null &&
          ArrayUtil.contains(myListenerHighlighter, (Object[])myEditor.getMarkupModel().getAllHighlighters())) {
        myListenerHighlighter.dispose();
        myListenerHighlighter = null;
      }
    }

    @Override
    public void keyTyped(@NotNull KeyEvent e) {}

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void mousePressed(@NotNull MouseEvent e) {}

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {}

    @Override
    public void mouseEntered(@NotNull MouseEvent e) {}

    @Override
    public void mouseExited(@NotNull MouseEvent e) {}
  }

  private void updateEditor() {
    Object source = getSource();

    String fileName = "Dummy." + (source instanceof FileType ? ((FileType)source).getDefaultExtension() : "txt");
    LightVirtualFile lightFile;
    if (source instanceof PsiViewerExtension) {
      lightFile = new LightVirtualFile(fileName, ((PsiViewerExtension)source).getDefaultFileType(), "");
    }
    else if (source instanceof LanguageFileType) {
      lightFile = new LightVirtualFile(fileName, ObjectUtils
        .chooseNotNull((Language)myDialectComboBox.getSelectedItem(), ((LanguageFileType)source).getLanguage()), "");
    }
    else if (source instanceof FileType) {
      lightFile = new LightVirtualFile(fileName, (FileType)source, "");
    }
    else {
      return;
    }
    EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, lightFile);
    try {
      myEditor.setHighlighter(highlighter);
    }
    catch (Throwable e) {
      LOG.warn(e);
    }
  }

  private void selectElement(@NotNull PsiElement element) {
    myStructureTreeModel.select(element, myPsiTree, path -> {});
  }

  private class EditorListener implements SelectionListener, DocumentListener, CaretListener {
    @Override
    public void caretPositionChanged(@NotNull CaretEvent e) {
      if (!available() || myEditor.getSelectionModel().hasSelection()) return;
      PsiElement rootPsiElement = myTreeStructure.getRootPsiElement();
      if (rootPsiElement == null) return;
      PsiElement rootElement = myTreeStructure.getRootPsiElement();
      int baseOffset = rootPsiElement.getTextRange().getStartOffset();
      int offset = myEditor.getCaretModel().getOffset() + baseOffset;
      String progressDialogTitle = DevPsiViewerBundle.message("psi.viewer.progress.dialog.get.element.at.offset");
      Callable<@Nullable PsiElement> finder = () -> InjectedLanguageUtilBase.findElementAtNoCommit(rootElement.getContainingFile(), offset);

      PsiElement element = computeSlowOperationsSafeInBgThread(myProject, progressDialogTitle, finder);

      if (element != null) {
        myBlockTree.selectNodeFromEditor(element);
        myStubTree.selectNodeFromEditor(element);
        selectElement(element);
      }
    }

    @Override
    public void selectionChanged(@NotNull SelectionEvent e) {
      if (!available() || !myEditor.getSelectionModel().hasSelection()) return;
      PsiElement rootElement = myTreeStructure.getRootPsiElement();
      if (rootElement == null) return;
      SelectionModel selection = myEditor.getSelectionModel();
      TextRange textRange = rootElement.getTextRange();
      int baseOffset = textRange != null ? textRange.getStartOffset() : 0;
      int start = selection.getSelectionStart() + baseOffset;
      int end = selection.getSelectionEnd() + baseOffset - 1;

      String progressDialogTitle = DevPsiViewerBundle.message("psi.viewer.progress.dialog.get.common.parent");
      Callable<PsiElement> finder =
        () -> findCommonParent(InjectedLanguageUtilBase.findElementAtNoCommit(rootElement.getContainingFile(), start),
                               InjectedLanguageUtilBase.findElementAtNoCommit(rootElement.getContainingFile(), end));

      PsiElement element = computeSlowOperationsSafeInBgThread(myProject, progressDialogTitle, finder);

      if (element != null) {
        if (myEditor.getContentComponent().hasFocus()) {
          myBlockTree.selectNodeFromEditor(element);
          myStubTree.selectNodeFromEditor(element);
        }
        selectElement(element);
      }
    }

    @Nullable
    private static PsiElement findCommonParent(PsiElement start, PsiElement end) {
      if (end == null || start == end) {
        return start;
      }
      TextRange endRange = end.getTextRange();
      PsiElement parent = start.getContext();
      while (parent != null && !parent.getTextRange().contains(endRange)) {
        parent = parent.getContext();
      }
      return parent;
    }

    private boolean available() {
      return myLastParsedTextHashCode == myNewDocumentHashCode && myEditor.getContentComponent().hasFocus();
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      myNewDocumentHashCode = event.getDocument().getText().hashCode();
    }
  }

  private static final class AutoExpandFocusListener extends FocusAdapter {
    private final JComboBox<?> myComboBox;
    private final Component myParent;

    private AutoExpandFocusListener(JComboBox<?> comboBox) {
      myComboBox = comboBox;
      myParent = UIUtil.findUltimateParent(myComboBox);
    }

    @Override
    public void focusGained(@NotNull FocusEvent e) {
      Component from = e.getOppositeComponent();
      if (!e.isTemporary() && from != null && !myComboBox.isPopupVisible() && isUnder(from, myParent)) {
        myComboBox.setPopupVisible(true);
      }
    }

    private static boolean isUnder(@NotNull Component component, Component parent) {
      while (component != null) {
        if (component == parent) return true;
        component = component.getParent();
      }
      return false;
    }
  }

  private static <T> T computeSlowOperationsSafeInBgThread(@NotNull Project project,
                                                           @NlsContexts.DialogTitle @NotNull String progressDialogTitle,
                                                           @NotNull Callable<T> callable) {

    return ProgressManager.getInstance().run(new Task.WithResult<>(project, progressDialogTitle, true) {
      @Override
      protected T compute(@NotNull ProgressIndicator indicator) throws RuntimeException {
        return ReadAction.nonBlocking(callable).executeSynchronously();
      }
    });
  }
}
