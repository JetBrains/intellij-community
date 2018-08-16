/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.resources;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.resources.ImplicitResourceCloser;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodMatcher;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.ui.UiUtils;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Bas Leijdekkers
 */
public class AutoCloseableResourceInspection extends ResourceInspection {


  private static final CallMatcher CLOSE = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, "close");
  private static final List<String> DEFAULT_IGNORED_TYPES =
    Arrays.asList("java.util.stream.Stream", "java.util.stream.IntStream", "java.util.stream.LongStream", "java.util.stream.DoubleStream");
  protected final MethodMatcher myMethodMatcher;
  final List<String> ignoredTypes = new ArrayList<>(DEFAULT_IGNORED_TYPES);
  @SuppressWarnings("PublicField")
  public boolean ignoreFromMethodCall = false;
  CallMatcher STREAM_HOLDING_RESOURCE = CallMatcher.staticCall("java.nio.file.Files", "lines", "walk", "list", "find");

  public AutoCloseableResourceInspection() {
    myMethodMatcher = new MethodMatcher()
      .add("java.util.Formatter", "format")
      .add("java.io.Writer", "append")
      .add("com.google.common.base.Preconditions", "checkNotNull")
      .add("org.hibernate.Session", "close")
      .add("java.io.PrintWriter", "printf")
      .finishDefault();
  }

  /**
   * Warning! This class have to manually save settings to xml using {@code readSettings()} and {@code writeSettings()} of its parent class
   **/
  @NotNull
  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new VerticalLayout(2));
    final ListTable table =
      new ListTable(new ListWrappingTableModel(ignoredTypes, InspectionGadgetsBundle.message("ignored.autocloseable.types.column.label")));
    final JPanel tablePanel =
      UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsBundle.message("choose.autocloseable.type.to.ignore.title"),
                                                   "java.lang.AutoCloseable");
    final ListTable table2 = new ListTable(
      new ListWrappingTableModel(Arrays.asList(myMethodMatcher.getClassNames(), myMethodMatcher.getMethodNamePatterns()),
                                 InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
                                 InspectionGadgetsBundle.message("method.name.regex"))) {
      @Override
      public void setEnabled(boolean enabled) {
        // hack to display correctly on initial opening of
        // inspection settings (otherwise it is always enabled)
        super.setEnabled(enabled && !ignoreFromMethodCall);
      }
    };
    final JPanel tablePanel2 = UiUtils.createAddRemoveTreeClassChooserPanel(table2, "Choose class");
    final JPanel wrapperPanel = new JPanel(new BorderLayout());
    wrapperPanel.setBorder(IdeBorderFactory.createTitledBorder("Ignore AutoCloseable instances returned from these methods", false));
    wrapperPanel.add(tablePanel2);
    panel.add(tablePanel);
    panel.add(wrapperPanel);
    final CheckBox checkBox =
      new CheckBox(InspectionGadgetsBundle.message("auto.closeable.resource.returned.option"), this, "ignoreFromMethodCall");
    checkBox.addChangeListener(e -> table2.setEnabled(!ignoreFromMethodCall));
    panel.add(checkBox);
    panel.add(new CheckBox(InspectionGadgetsBundle.message("any.method.may.close.resource.argument"), this, "anyMethodMayClose"));
    return panel;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("auto.closeable.resource.display.name");
  }

  @NotNull
  @Override
  public String getID() {
    return "resource"; // matches Eclipse inspection
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    final String text = type.getPresentableText();
    return InspectionGadgetsBundle.message("auto.closeable.resource.problem.descriptor", text);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final boolean buildQuickfix = ((Boolean)infos[1]).booleanValue();
    if (!buildQuickfix) {
      return null;
    }
    return new AutoCloseableResourceFix();
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    for (Element option : node.getChildren("option")) {
      final String name = option.getAttributeValue("name");
      if ("ignoredTypes".equals(name)) {
        final String ignoredTypesString = option.getAttributeValue("value");
        if (ignoredTypesString != null) {
          ignoredTypes.clear();
          parseString(ignoredTypesString, ignoredTypes);
        }
      }
    }
    myMethodMatcher.readSettings(node);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    writeBooleanOption(node, "ignoreFromMethodCall", false);
    writeBooleanOption(node, "anyMethodMayClose", true);
    if (!DEFAULT_IGNORED_TYPES.equals(ignoredTypes)) {
      final String ignoredTypesString = formatString(ignoredTypes);
      node.addContent(new Element("option").setAttribute("name", "ignoredTypes").setAttribute("value", ignoredTypesString));
    }
    myMethodMatcher.writeSettings(node);
  }

  @Override
  protected boolean isResourceCreation(PsiExpression expression) {
    return TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE) &&
           (isStreamHoldingResource(expression)
            || !TypeUtils.expressionHasTypeOrSubtype(expression, ignoredTypes));
  }

  private boolean isStreamHoldingResource(PsiExpression expression) {
    return STREAM_HOLDING_RESOURCE.matches(tryCast(expression, PsiMethodCallExpression.class));
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel7OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AutoCloseableResourceVisitor();
  }

  private class AutoCloseableResourceFix extends InspectionGadgetsFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("auto.closeable.resource.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (methodCallExpression == null) {
        return;
      }
      myMethodMatcher.add(methodCallExpression);
    }
  }

  private class AutoCloseableResourceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (isSafelyClosedResource(expression)) {
        return;
      }
      registerNewExpressionError(expression, expression.getType(), Boolean.FALSE);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (ignoreFromMethodCall || myMethodMatcher.matches(expression) || isSafelyClosedResource(expression)) {
        return;
      }
      PsiExpression returnedValue = JavaMethodContractUtil.findReturnedValue(expression);
      PsiExpression[] arguments = expression.getArgumentList().getExpressions();
      PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
      if (qualifier == returnedValue) return;
      for (PsiExpression argument : arguments) {
        if (returnedValue == argument) {
          return;
        }
      }
      registerMethodCallError(expression, expression.getType(), !isStreamHoldingResource(expression));
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      if (!expression.isConstructor()) {
        return;
      }
      final PsiType type = PsiMethodReferenceUtil.getQualifierType(expression);
      if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE)) {
        return;
      }
      for (String ignoredType : ignoredTypes) {
        if (InheritanceUtil.isInheritor(type, ignoredType)) {
          return;
        }
      }
      registerError(expression, type, Boolean.FALSE);
    }

    private boolean isSafelyClosedResource(PsiExpression expression) {
      if (!isResourceCreation(expression)) {
        return true;
      }
      if (CLOSE.test(ExpressionUtils.getCallForQualifier(expression))) return true;
      final PsiVariable variable = ResourceInspection.getVariable(expression);
      if (variable instanceof PsiResourceVariable || isResourceEscapingFromMethod(variable, expression)) return true;
      if (variable == null) return false;
      return StreamEx.of(Extensions.getExtensions(ImplicitResourceCloser.EP_NAME))
                     .anyMatch(closer -> closer.isSafelyClosed(variable));
    }
  }
}
