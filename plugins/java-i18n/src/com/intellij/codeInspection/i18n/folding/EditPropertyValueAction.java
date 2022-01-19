// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n.folding;

import com.intellij.codeInsight.folding.impl.EditorFoldingInfo;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class EditPropertyValueAction extends BaseRefactoringAction {
  private static final Key<Boolean> EDITABLE_PROPERTY_VALUE = Key.create("editable.property.value");
  private static final KeyStroke SHIFT_ENTER = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK);

  @Override
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  protected boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    return false;
  }

  @Nullable
  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new Handler();
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element,
                                                        @NotNull Editor editor,
                                                        @NotNull PsiFile file,
                                                        @NotNull DataContext context) {
    return isEnabled(editor);
  }

  private static class Handler implements RefactoringActionHandler {
    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
      doEdit(editor);
    }

    @Override
    public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {}
  }

  public static boolean isEnabled(@NotNull Editor editor) {
    if (!(editor instanceof EditorImpl) || editor.getProject() == null) {
      return false;
    }
    FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(editor.getCaretModel().getOffset());
    if (region == null) return false;
    return getEditableElement(region) != null;
  }

  @Nullable
  public static PsiElement getEditableElement(@NotNull FoldRegion region) {
    PsiElement psiElement = EditorFoldingInfo.get(region.getEditor()).getPsiElement(region);
    return psiElement == null || psiElement.getUserData(EDITABLE_PROPERTY_VALUE) == null ? null : psiElement;
  }

  public static void doEdit(@NotNull Editor editor) {
    if (!(editor instanceof EditorImpl) || editor.getProject() == null) {
      return;
    }
    FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(editor.getCaretModel().getOffset());
    if (region == null) return;
    PsiElement psiElement = getEditableElement(region);
    PropertyFoldingEditHandler handler = new PropertyFoldingEditHandler(psiElement);
    if (!handler.isValid()) return;
    VisualPosition regionPosition = editor.offsetToVisualPosition(region.getStartOffset());
    VisualPosition caretPosition = editor.getCaretModel().getVisualPosition();
    int placeholderOffset =
      ((EditorImpl)editor).visualColumnToOffsetInFoldRegion(region, caretPosition.column - regionPosition.column, false);
    int valueOffset = handler.placeholderToValueOffset(placeholderOffset);
    String value = StringUtil.notNullize(handler.getValue());
    Pair<String, Integer> unescaped = unescape(value, valueOffset);

    Ref<Boolean> manuallyResized = new Ref<>();
    Ref<JBPopup> popupRef = new Ref<>();
    EditorTextField textField = new EditorTextField(unescaped.first, editor.getProject(), FileTypes.PLAIN_TEXT) {
      @Override
      protected @NotNull EditorEx createEditor() {
        EditorEx e = super.createEditor();
        e.getCaretModel().moveToOffset(Math.max(0, Math.min(e.getDocument().getTextLength(), unescaped.second)));
        e.setHorizontalScrollbarVisible(true);
        e.getDocument().addDocumentListener(new DocumentListener() {
          @Override
          public void documentChanged(@NotNull DocumentEvent event) {
            if (manuallyResized.isNull()) {
              popupRef.get().pack(true, true);
            }
          }
        });
        return e;
      }

      @Override
      protected boolean shouldHaveBorder() {
        return false;
      }
    };
    textField.setOneLineMode(false);
    textField.setFontInheritedFromLAF(false);

    MyShiftEnterAction shiftEnterAction = new MyShiftEnterAction();

    JPanel panel = new JPanel(new GridBagLayout()) {
      @Override
      public Dimension getPreferredSize() {
        int MIN_WIDTH = JBUI.scale(350);
        int MAX_WIDTH = JBUI.scale(550);
        Dimension size = super.getPreferredSize();
        int width = size.width;
        int height = size.height;
        if (width > MAX_WIDTH) {
          setSize(MAX_WIDTH, height);
          validate();
          size = super.getPreferredSize();
          width = size.width;
          height = size.height;
        }
        return new Dimension(Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width)), Math.min(MAX_WIDTH, height));
      }
    };
    panel.setBackground(textField.getBackground());
    int topBottomGap = 2;
    int sideGap = 4;
    int betweenGap = 1;
    GridBagConstraints c = new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                  new JBInsets(topBottomGap, sideGap, topBottomGap, betweenGap), 0, 0);
    panel.add(textField, c);
    JComponent button = createNewLineButton(textField, editor.getLineHeight(), shiftEnterAction);
    c.gridx = 1;
    c.weightx = 0;
    c.weighty = 0;
    c.fill = GridBagConstraints.NONE;
    c.insets = new JBInsets(topBottomGap, betweenGap, topBottomGap, sideGap);
    panel.add(button, c);

    JBPopup popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(panel, textField)
      .setRequestFocus(true)
      .setResizable(true)
      .setLocateByContent(true)
      .setCancelOnWindowDeactivation(false)
      .createPopup();
    popupRef.set(popup);
    ((AbstractPopup)popup).addResizeListener(() -> manuallyResized.set(Boolean.TRUE), popup);

    new MyEnterAction(textField, region, popup, handler)
      .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), textField);
    shiftEnterAction
      .registerCustomShortcutSet(new CustomShortcutSet(SHIFT_ENTER), textField);

    Point location = editor.visualPositionToXY(new VisualPosition(regionPosition.line, regionPosition.column + 1));
    location.translate(-JBUI.scale(sideGap), -JBUI.scale(topBottomGap));
    popup.show(new RelativePoint(editor.getContentComponent(), location));
    LightweightHint hint = showTooltip(editor, handler.getFile(), handler.getKey());
    if (hint != null) {
      popup.addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          hint.hide();
        }
      });
    }
  }

  @SuppressWarnings("AssignmentToForLoopParameter")
  private static Pair<String, Integer> unescape(String value, int offset) {
    StringBuilder b = new StringBuilder(value);
    int[] offsets = new int[]{offset};
    boolean escaped = false;
    for (int i = 0; i < b.length(); i++) {
      char c = b.charAt(i);
      if (c == '\\') {
        if (escaped) {
          escaped = false;
          i = replaceEscapePair(b, i, "\\", offsets);
        }
        else {
          escaped = true;
        }
      }
      else if (escaped) {
        escaped = false;
        String replacement;
        switch (c) {
          case '\n':
            replacement = "";
            break;
          case 'r':
            replacement = "\n";
            break;
          case 'n':
            replacement = "\r";
            break;
          case 'u':
          case 'U':
            replacement = null;
            break;
          default:
            replacement = Character.toString(c);
            break;
        }
        if (replacement != null) {
          i = replaceEscapePair(b, i, replacement, offsets);
        }
      }
    }
    String result = StringUtil.convertLineSeparators(b.toString(), "\n", offsets);
    return Pair.create(result, offsets[0]);
  }

  private static int replaceEscapePair(StringBuilder b, int midOffset, String replacement, int[] offsetToKeep) {
    b.replace(midOffset - 1, midOffset + 1, replacement);
    int shift = replacement.length() - 2;
    if (offsetToKeep[0] > midOffset) offsetToKeep[0] += shift;
    return midOffset + shift;
  }

  private static JComponent createNewLineButton(EditorTextField textField, int minSize, AnAction shiftEnterAction) {
    Presentation presentation = shiftEnterAction.getTemplatePresentation();
    int size = Math.max(JBUI.scale(16), minSize);
    Dimension d = new JBDimension(size, size, true);
    ActionButton button = new ActionButton(shiftEnterAction, presentation, ActionPlaces.UNKNOWN, d) {
      @Override
      protected DataContext getDataContext() {
        return DataManager.getInstance().getDataContext(textField);
      }

      @Override
      public Insets getInsets() {
        return JBInsets.emptyInsets();
      }
    };
    button.setLook(ActionButtonLook.INPLACE_LOOK);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return button;
  }

  private static LightweightHint showTooltip(@NotNull Editor editor, @Nullable VirtualFile file, @Nullable @NlsSafe String key) {
    if (file == null && key == null) return null;
    JPanel panel = new JPanel();
    panel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
    if (file != null) {
      panel.add(new JLabel(file.getPresentableName() + (key == null ? "" : ": "), IconUtil.getIcon(file, 0, editor.getProject()),
                           SwingConstants.LEFT));
    }
    if (key != null) {
      panel.add(new JLabel(key, AllIcons.Nodes.Property, SwingConstants.LEFT));
    }
    return EditPropertyValueTooltipManager.showTooltip(editor, panel, true);
  }

  public static void registerFoldedElement(@NotNull PsiElement element, @NotNull Document document) {
    element.putUserData(EDITABLE_PROPERTY_VALUE, Boolean.TRUE);
    EditPropertyValueTooltipManager.initializeForDocument(document);
  }

  private static final class MyEnterAction extends AnAction {
    private final EditorTextField field;
    private final FoldRegion foldRegion;
    private final JBPopup popup;
    private final PropertyFoldingEditHandler handler;

    private MyEnterAction(EditorTextField field,
                          FoldRegion region,
                          JBPopup popup,
                          PropertyFoldingEditHandler handler) {
      this.field = field;
      foldRegion = region;
      this.popup = popup;
      this.handler = handler;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      PsiFile targetPsiFile = handler.getPsiFile();
      if (targetPsiFile == null) return;
      Project project = field.getProject();
      String newValue = field.getText();
      Editor fieldEditor = field.getEditor();
      if (fieldEditor == null) return;
      int valueOffset = fieldEditor.getCaretModel().getOffset();
      Editor editor = foldRegion.getEditor();
      JComponent editorComponent = editor.getContentComponent();
      focusAndRun(editorComponent, () -> {
        WriteCommandAction.runWriteCommandAction(project, JavaI18nBundle.message("command.name.edit.property.value"), null, () -> {
          handler.setValue(newValue.replace("\n", "\\n"));
          String oldPlaceholder = foldRegion.getPlaceholderText();
          String newPlaceholder = handler.getPlaceholder();
          editor.getFoldingModel().runBatchFoldingOperation(() -> foldRegion.setPlaceholderText(newPlaceholder));
          VisualPosition regionStartPosition = editor.offsetToVisualPosition(foldRegion.getStartOffset());
          int placeholderOffset =
            Math.max(1, Math.min(newPlaceholder.length() - 1, handler.valueToPlaceholderOffset(valueOffset)));
          int placeholderColumn = ((EditorImpl)editor).offsetToVisualColumnInFoldRegion(foldRegion, placeholderOffset, false);
          editor.getCaretModel().moveToVisualPosition(
            new VisualPosition(regionStartPosition.line, regionStartPosition.column + placeholderColumn));
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          UndoManager.getInstance(project).undoableActionPerformed(new UndoableAction() {
            @Override
            public void undo() {
              if (foldRegion.isValid()) {
                editor.getFoldingModel().runBatchFoldingOperation(() -> foldRegion.setPlaceholderText(oldPlaceholder));
              }
            }

            @Override
            public void redo() {
              if (foldRegion.isValid()) {
                editor.getFoldingModel().runBatchFoldingOperation(() -> foldRegion.setPlaceholderText(newPlaceholder));
              }
            }

            @Override
            public DocumentReference @Nullable [] getAffectedDocuments() {
              return null;
            }

            @Override
            public boolean isGlobal() {
              return false;
            }
          });
        }, targetPsiFile);
        editorComponent.paintImmediately(new Rectangle(editorComponent.getSize()));
        popup.cancel();
      });
    }

    private static void focusAndRun(@NotNull Component component, @NotNull Runnable runnable) {
      component.requestFocus();
      if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == component) {
        runnable.run();
      }
      else {
        component.addFocusListener(new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent e) {
            component.removeFocusListener(this);
            runnable.run();
          }
        });
      }
    }
  }

  private static final class MyShiftEnterAction extends EditorAction {
    private MyShiftEnterAction() {
      super(new Handler());
      Presentation presentation = getTemplatePresentation();
      presentation.setDescription(JavaI18nBundle.message("action.description.new.line.0", KeymapUtil.getKeystrokeText(SHIFT_ENTER)));
      presentation.setIcon(AllIcons.Actions.SearchNewLine);
      presentation.setHoveredIcon(AllIcons.Actions.SearchNewLineHover);
    }

    private static class Handler extends EditorWriteActionHandler {
      @Override
      public void executeWriteAction(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
        EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER).execute(editor, caret, dataContext);
      }
    }
  }
}
