/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.coverage;

import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.coverage.actions.ShowCoveringTestsAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.ActiveGutterRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.source.tree.java.PsiSwitchStatementImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.rt.coverage.data.*;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.SideBorder2;
import com.intellij.util.Function;
import com.intellij.util.ImageLoader;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * @author ven
 */
public class  CoverageLineMarkerRenderer implements ActiveGutterRenderer {

  private static final Logger LOG = Logger.getInstance("#com.intellij.coverage.CoverageLineMarkerRendererFactory");
  private static final int THICKNESS = 8;
  private final TextAttributesKey myKey;
  private final ClassData myClassData;
  private final TreeMap<Integer, LineData> myLines;
  private final boolean myCoverageByTestApplicable;
  private final Function<Integer, Integer> myNewToOldConverter;
  private final Function<Integer, Integer> myOldToNewConverter;

  CoverageLineMarkerRenderer(final TextAttributesKey textAttributesKey, final ClassData classData, final TreeMap<Integer, LineData> lines, final boolean coverageByTestApplicable,
                             final Function<Integer, Integer> newToOldConverter,
                             final Function<Integer, Integer> oldToNewConverter) {
    myKey = textAttributesKey;
    myClassData = classData;
    myLines = lines;
    myCoverageByTestApplicable = coverageByTestApplicable;
    myNewToOldConverter = newToOldConverter;
    myOldToNewConverter = oldToNewConverter;
  }

  public void paint(Editor editor, Graphics g, Rectangle r) {
    final TextAttributes color = editor.getColorsScheme().getAttributes(myKey);
    g.setColor(color.getForegroundColor());
    int height = r.height + editor.getLineHeight();
    g.fillRect(0, r.y, THICKNESS, height);
    final LineData lineData = getLineData(editor.xyToLogicalPosition(new Point(0, r.y)).line);
    if (lineData != null && lineData.isCoveredByOneTest()) {
      g.drawImage( ImageLoader.loadFromResource("/gutter/unique.png"), 0, r.y, 8, 8, editor.getComponent());
    }
  }

  public static CoverageLineMarkerRenderer getRenderer(int lineNumber, final ClassData qualifiedName, final TreeMap<Integer, LineData> lines,
                                                       final boolean coverageByTestApplicable, final Function<Integer, Integer> newToOldConverter,
                                                       final Function<Integer, Integer> oldToNewConverter) {
    final LineData lineData = lines.get(lineNumber);
    if (lineData != null) {
      switch (lineData.getStatus()) {
        case LineCoverage.FULL:
          return new CoverageLineMarkerRenderer(CodeInsightColors.LINE_FULL_COVERAGE, qualifiedName, lines, coverageByTestApplicable, newToOldConverter,
                                                oldToNewConverter);
        case LineCoverage.PARTIAL:
          return new CoverageLineMarkerRenderer(CodeInsightColors.LINE_PARTIAL_COVERAGE, qualifiedName, lines,
                                                coverageByTestApplicable,newToOldConverter,oldToNewConverter);
        case LineCoverage.NONE:
          return new CoverageLineMarkerRenderer(CodeInsightColors.LINE_NONE_COVERAGE, qualifiedName, lines, coverageByTestApplicable, newToOldConverter,
                                                oldToNewConverter);
      }
    }

    return new CoverageLineMarkerRenderer(CodeInsightColors.LINE_NONE_COVERAGE, qualifiedName, lines, coverageByTestApplicable, newToOldConverter,
                                          oldToNewConverter);
  }

  public boolean canDoAction(final MouseEvent e) {
    return myCoverageByTestApplicable && e.getX() < THICKNESS;
  }

  public void doAction(final Editor editor, final MouseEvent e) {
    e.consume();
    final JComponent comp = (JComponent)e.getComponent();
    final JLayeredPane layeredPane = comp.getRootPane().getLayeredPane();
    final Point point = SwingUtilities.convertPoint(comp, THICKNESS, e.getY(), layeredPane);
    showHint(editor, point, editor.xyToLogicalPosition(e.getPoint()).line);
  }

  private void showHint(final Editor editor, final Point point, final int lineNumber) {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(createActionsToolbar(editor, lineNumber), BorderLayout.NORTH);

    final LineData lineData = getLineData(lineNumber);
    final EditorImpl uEditor;
    if (lineData != null && lineData.getStatus() != LineCoverage.NONE) {
      final EditorFactory factory = EditorFactory.getInstance();
      final Document doc = factory.createDocument(getReport(editor, lineNumber));
      doc.setReadOnly(true);
      uEditor = (EditorImpl)factory.createEditor(doc, editor.getProject());
      panel.add(EditorFragmentComponent.createEditorFragmentComponent(uEditor, 0, doc.getLineCount(), false, false), BorderLayout.CENTER);
    } else {
      uEditor = null;
    }


    final LightweightHint hint = new LightweightHint(panel){
      @Override
      public void hide() {
        if (uEditor != null) EditorFactory.getInstance().releaseEditor(uEditor);
        super.hide();

      }
    };
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, point,
        HintManagerImpl.HIDE_BY_ANY_KEY | HintManagerImpl.HIDE_BY_TEXT_CHANGE | HintManagerImpl.HIDE_BY_OTHER_HINT | HintManagerImpl.HIDE_BY_SCROLLING, -1, false);
  }

  private String getReport(final Editor editor, final int lineNumber) {
    final StringBuffer buf = new StringBuffer();
    final LineData lineData = getLineData(lineNumber);
    buf.append("Hits: ").append(lineData.getHits()).append("\n");

    final Document document = editor.getDocument();
    final Project project = editor.getProject();
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    final int startOffset = document.getLineStartOffset(lineNumber);
    final int endOffset = document.getLineEndOffset(lineNumber);
    final List<PsiExpression> expressions = new ArrayList<PsiExpression>();

    for(int offset = startOffset; offset < endOffset; offset++) {
      PsiElement parent = PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), PsiStatement.class);
      PsiElement condition = null;
      if (parent instanceof PsiIfStatement) {
        condition = ((PsiIfStatement)parent).getCondition();
      } else if (parent instanceof PsiSwitchStatement) {
        condition = ((PsiSwitchStatement)parent).getExpression();
      } else if (parent instanceof PsiDoWhileStatement) {
        condition = ((PsiDoWhileStatement)parent).getCondition();
      } else if (parent instanceof PsiForStatement) {
        condition = ((PsiForStatement)parent).getCondition();
      } else if (parent instanceof PsiWhileStatement) {
        condition = ((PsiWhileStatement)parent).getCondition();
      } else if (parent instanceof PsiForeachStatement) {
        condition = ((PsiForeachStatement)parent).getIteratedValue();
      } else if (parent instanceof PsiAssertStatement) {
        condition = ((PsiAssertStatement)parent).getAssertCondition();
      }
      if (condition != null) {
        try {
          final ControlFlow controlFlow = ControlFlowFactory.getInstance(project).getControlFlow(
              parent, AllVariablesControlFlowPolicy.getInstance());
          for (Instruction instruction : controlFlow.getInstructions()) {
            if (instruction instanceof ConditionalBranchingInstruction) {
              final PsiExpression expression = ((ConditionalBranchingInstruction)instruction).expression;
              if (!expressions.contains(expression)) {
                expressions.add(expression);
              }
            }
          }
        }
        catch (AnalysisCanceledException e) {
          return buf.toString();
        }
      }
    }

    final String indent = "    ";
    try {
      int idx = 0;
      if (lineData.getJumps() != null) {
        for (Object o : lineData.getJumps()) {
          final JumpData jumpData = (JumpData)o;
          if (jumpData.getTrueHits() + jumpData.getFalseHits() > 0) {
            final PsiExpression expression = expressions.get(idx++);
            final PsiElement parentExpression = expression.getParent();
            boolean reverse = parentExpression instanceof PsiBinaryExpression && ((PsiBinaryExpression)parentExpression).getOperationSign().getTokenType() == JavaTokenType.OROR || parentExpression instanceof PsiDoWhileStatement || parentExpression instanceof PsiAssertStatement;
            buf.append(indent).append(expression.getText()).append("\n");
            buf.append(indent).append(indent).append("true hits: ").append(reverse ? jumpData.getFalseHits() : jumpData.getTrueHits()).append("\n");
            buf.append(indent).append(indent).append("false hits: ").append(reverse ? jumpData.getTrueHits() : jumpData.getFalseHits()).append("\n");
          }
        }
      }

      if (lineData.getSwitches() != null) {
        for (Object o : lineData.getSwitches()) {
          final SwitchData switchData = (SwitchData)o;
          final PsiExpression conditionExpression = expressions.get(idx++);
          buf.append(indent).append(conditionExpression.getText()).append("\n");
          if (hasDefaultLabel(conditionExpression)) {
            buf.append(indent).append(indent).append("Default hits: ").append(switchData.getDefaultHits()).append("\n");
          }
          int i = 0;
          for (int key : switchData.getKeys()) {
            buf.append(indent).append(indent).append("case ").append(key).append(": ").append(switchData.getHits()[i++]).append("\n");
          }
        }
      }
    }
    catch (Exception e) {
      LOG.info(e); 
      return "Hits: " + getLineData(lineNumber).getHits();
    }
    return buf.toString();
  }

  private static boolean hasDefaultLabel(final PsiExpression conditionExpression) {
    boolean hasDefault = false;
    final PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(conditionExpression, PsiSwitchStatement.class);
    final PsiCodeBlock body = ((PsiSwitchStatementImpl)conditionExpression.getParent()).getBody();
    if (body != null) {
      final PsiElement bodyElement = body.getFirstBodyElement();
      if (bodyElement != null) {
        PsiSwitchLabelStatement label = PsiTreeUtil.getNextSiblingOfType(bodyElement, PsiSwitchLabelStatement.class);
        while (label != null) {
          if (label.getEnclosingSwitchStatement() == switchStatement) {
            hasDefault |= label.isDefaultCase();
          }
          label = PsiTreeUtil.getNextSiblingOfType(label, PsiSwitchLabelStatement.class);
        }
      }
    }
    return hasDefault;
  }

  private JComponent createActionsToolbar(final Editor editor, final int lineNumber) {
    final DefaultActionGroup group = new DefaultActionGroup();
    final JComponent editorComponent = editor.getComponent();
    final GotoPreviousGoveredLineAction prevAction = new GotoPreviousGoveredLineAction(editor, lineNumber);
    final GotoNextCoveredLineAction nextAction = new GotoNextCoveredLineAction(editor, lineNumber);

    group.add(prevAction);
    group.add(nextAction);

    prevAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_MASK|InputEvent.SHIFT_MASK)), editorComponent);
    nextAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_MASK|InputEvent.SHIFT_MASK)), editorComponent);

    final LineData lineData = getLineData(lineNumber);
    group.add(new ShowCoveringTestsAction(myClassData != null ? myClassData.getName() : null, lineData));

    group.add(new HideCoverageInfoAction(editor));

    final JComponent toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.FILEHISTORY_VIEW_TOOLBAR, group, true).getComponent();

    final Color background = ((EditorEx)editor).getBackroundColor();
    final Color foreground = editor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
    toolbar.setBackground(background);
    toolbar.setBorder(new SideBorder2(foreground, foreground, lineData == null || lineData.getStatus() == LineCoverage.NONE ? foreground : null, foreground, 1));
    return toolbar;
  }

  public void moveToLine(final int lineNumber, final Editor editor) {
    final int firstOffset = editor.getDocument().getLineStartOffset(lineNumber);
    editor.getCaretModel().moveToOffset(firstOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);

    editor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
      public void run() {
        Point p = editor.visualPositionToXY(editor.offsetToVisualPosition(firstOffset));
        EditorGutterComponentEx editorComponent = (EditorGutterComponentEx)editor.getGutter();
        JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
        p = SwingUtilities.convertPoint(editorComponent, THICKNESS, p.y, layeredPane);
        showHint(editor, p, lineNumber);
      }
    });
  }

  @Nullable
  public LineData getLineData(int lineNumber) {
    return myLines != null ? myLines.get(myNewToOldConverter != null ? myNewToOldConverter.fun(lineNumber).intValue() : lineNumber) : null;
  }

  public Color getErrorStripeColor(final Editor editor) {
    return editor.getColorsScheme().getAttributes(myKey).getErrorStripeColor();
  }

  private class GotoPreviousGoveredLineAction extends BaseGotoCoveredLineAction {

    public GotoPreviousGoveredLineAction(final Editor editor, final int lineNumber) {
      super(editor, lineNumber);
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_OCCURENCE));
    }

    protected boolean hasNext(final int idx, final List<Integer> list) {
      return idx > 0;
    }

    protected int next(final int idx) {
      return idx - 1;
    }
  }

  private class GotoNextCoveredLineAction extends BaseGotoCoveredLineAction {

    public GotoNextCoveredLineAction(final Editor editor, final int lineNumber) {
      super(editor, lineNumber);
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_OCCURENCE));
    }

    protected boolean hasNext(final int idx, final List<Integer> list) {
      return idx < list.size() - 1;
    }

    protected int next(final int idx) {
      return idx + 1;
    }
  }

  private abstract class BaseGotoCoveredLineAction extends AnAction {
    private final Editor myEditor;
    private final int myLineNumber;

    public BaseGotoCoveredLineAction(final Editor editor, final int lineNumber) {
      myEditor = editor;
      myLineNumber = lineNumber;
    }

    public void actionPerformed(final AnActionEvent e) {
      final Integer lineNumber = getLineEntry();
      if (lineNumber != null) {
        moveToLine(lineNumber.intValue(), myEditor);
      }
    }

    protected abstract boolean hasNext(int idx, List<Integer> list);
    protected abstract int next(int idx);

    @Nullable
    private Integer getLineEntry() {
      final ArrayList<Integer> list = new ArrayList<Integer>(myLines.keySet());
      Collections.sort(list);
      final LineData data = getLineData(myLineNumber);
      final int currentStatus = data != null ? data.getStatus() : LineCoverage.NONE;
      int idx = list.indexOf(myNewToOldConverter != null ? myNewToOldConverter.fun(myLineNumber).intValue() : myLineNumber);
      while (hasNext(idx, list)) {
        final int index = next(idx);
        final LineData lineData = myLines.get(list.get(index));
        idx = index;
        if (lineData != null && lineData.getStatus() != currentStatus) {
          final Integer line = list.get(idx);
          if (myOldToNewConverter != null) {
            final int newLine = myOldToNewConverter.fun(line).intValue();
            if (newLine != 0) return newLine;
          } else {
            return line;
          }
        }
      }
      return null;
    }

    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(getLineEntry() != null);
    }
  }

  private static class HideCoverageInfoAction extends AnAction {
    private final Editor myEditor;
    private HideCoverageInfoAction(Editor editor) {
      super("Hide coverage information", "Hide coverage information", IconLoader.getIcon("/actions/cancel.png"));
      myEditor = editor;
    }

    public void actionPerformed(final AnActionEvent e) {
      CoverageDataManager.getInstance(myEditor.getProject()).chooseSuite(null);
    }
  }
}
