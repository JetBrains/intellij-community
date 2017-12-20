/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.refactoring.ui;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.PairFunction;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class MethodOrClosureScopeChooser {
  private static final Logger LOG = Logger.getInstance(MethodOrClosureScopeChooser.class);

  @NonNls private static final String USE_SUPER_METHOD_OF = "Change base method";
  @NonNls private static final String CHANGE_USAGES_OF = "Change usages";

  public interface JBPopupOwner {
    JBPopup get();
  }

  /**
   * @param callback is invoked if any scope was chosen. The first arg is this scope and the second arg is a psielement to search for (super method of chosen method or
   *                 variable if the scope is a closure)
   */
  public static JBPopup create(List<? extends GrParametersOwner> scopes,
                               final Editor editor,
                               final JBPopupOwner popupRef,
                               final PairFunction<GrParametersOwner, PsiElement, Object> callback) {
    final JPanel panel = new JPanel(new BorderLayout());
    final JCheckBox superMethod = new JCheckBox(USE_SUPER_METHOD_OF, true);
    superMethod.setMnemonic('U');
    panel.add(superMethod, BorderLayout.SOUTH);
    final JBList list = new JBList(scopes.toArray());
    list.setVisibleRowCount(5);
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        final String text;
        if (value instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)value;
          text = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                            PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                            PsiFormatUtilBase.SHOW_NAME |
                                            PsiFormatUtilBase.SHOW_PARAMETERS,
                                            PsiFormatUtilBase.SHOW_TYPE);
          final int flags = Iconable.ICON_FLAG_VISIBILITY;
          final Icon icon = method.getIcon(flags);
          if (icon != null) setIcon(icon);
        }
        else {
          LOG.assertTrue(value instanceof GrClosableBlock);
          setIcon(JetgroovyIcons.Groovy.Groovy_16x16);
          text = "{...}";
        }
        setText(text);
        return this;
      }
    });
    list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setSelectedIndex(0);
    final List<RangeHighlighter> highlighters = new ArrayList<>();
    final TextAttributes attributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    list.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        final GrParametersOwner selectedMethod = (GrParametersOwner)list.getSelectedValue();
        if (selectedMethod == null) return;
        dropHighlighters(highlighters);
        updateView(selectedMethod, editor, attributes, highlighters, superMethod);
      }
    });
    updateView(scopes.get(0), editor, attributes, highlighters, superMethod);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(list);
    scrollPane.setBorder(null);
    panel.add(scrollPane, BorderLayout.CENTER);

    final List<Pair<ActionListener, KeyStroke>> keyboardActions = Collections.singletonList(
      Pair.<ActionListener, KeyStroke>create(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final GrParametersOwner ToSearchIn = (GrParametersOwner)list.getSelectedValue();
          final JBPopup popup = popupRef.get();
          if (popup != null && popup.isVisible()) {
            popup.cancel();
          }


          final PsiElement toSearchFor;
          if (ToSearchIn instanceof GrMethod) {
            final GrMethod method = (GrMethod)ToSearchIn;
            toSearchFor = superMethod.isEnabled() && superMethod.isSelected() ? method.findDeepestSuperMethod() : method;
          }
          else {
            toSearchFor = superMethod.isEnabled() && superMethod.isSelected() ? ToSearchIn.getParent() : null;
          }
          IdeFocusManager.findInstance().doWhenFocusSettlesDown(() -> callback.fun(ToSearchIn, toSearchFor), ModalityState.current());
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));


    return JBPopupFactory.getInstance().createComponentPopupBuilder(panel, list)
      .setTitle("Introduce parameter to")
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setKeyboardActions(keyboardActions).addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(LightweightWindowEvent event) {
          dropHighlighters(highlighters);
        }
      }).createPopup();
  }


  public static void updateView(GrParametersOwner selectedMethod,
                                Editor editor,
                                TextAttributes attributes,
                                List<RangeHighlighter> highlighters,
                                JCheckBox superMethod) {
    final MarkupModel markupModel = editor.getMarkupModel();
    final TextRange textRange = selectedMethod.getTextRange();
    final RangeHighlighter rangeHighlighter =
      markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1, attributes,
                                      HighlighterTargetArea.EXACT_RANGE);
    highlighters.add(rangeHighlighter);
    if (selectedMethod instanceof GrMethod) {
      superMethod.setText(USE_SUPER_METHOD_OF);
      superMethod.setEnabled(((GrMethod)selectedMethod).findDeepestSuperMethod() != null);
    }
    else {
      superMethod.setText(CHANGE_USAGES_OF);
      superMethod.setEnabled(findVariableToUse(selectedMethod) != null);
    }
  }

  @Nullable
  public static GrVariable findVariableToUse(@NotNull GrParametersOwner owner) {
    final PsiElement parent = owner.getParent();
    if (parent instanceof GrVariable) return (GrVariable)parent;
    if (parent instanceof GrAssignmentExpression &&
        ((GrAssignmentExpression)parent).getRValue() == owner &&
        !((GrAssignmentExpression)parent).isOperatorAssignment()) {
      final GrExpression lValue = ((GrAssignmentExpression)parent).getLValue();
      if (lValue instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)lValue).resolve();
        if (resolved instanceof GrVariable) {
          return (GrVariable)resolved;
        }
      }
    }
    return null;
  }

  private static void dropHighlighters(List<RangeHighlighter> highlighters) {
    for (RangeHighlighter highlighter : highlighters) {
      highlighter.dispose();
    }
    highlighters.clear();
  }
}
