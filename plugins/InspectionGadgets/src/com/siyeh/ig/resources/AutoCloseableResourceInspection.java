// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.resources;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.resources.ImplicitResourceCloser;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.CheckBox;
import com.intellij.util.ui.UI;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodMatcher;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
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
    Arrays.asList("java.util.stream.Stream",
                  "java.util.stream.IntStream",
                  "java.util.stream.LongStream",
                  "java.util.stream.DoubleStream",
                  "java.io.ByteArrayOutputStream",
                  "java.io.ByteArrayInputStream",
                  "java.io.StringBufferInputStream",
                  "java.io.CharArrayWriter",
                  "java.io.CharArrayReader",
                  "java.io.StringWriter",
                  "java.io.StringReader",
                  "java.util.Formatter",
                  "java.util.Scanner",
                  "org.springframework.context.ConfigurableApplicationContext",
                  "io.micronaut.context.ApplicationContext");

  protected final MethodMatcher myMethodMatcher;
  final List<String> ignoredTypes = new ArrayList<>(DEFAULT_IGNORED_TYPES);
  @SuppressWarnings("PublicField")
  public boolean ignoreFromMethodCall = false;
  public boolean ignoreConstructorMethodReferences = true;
  public boolean ignoreGettersReturningResource = true;
  CallMatcher STREAM_HOLDING_RESOURCE = CallMatcher.staticCall("java.nio.file.Files", "lines", "walk", "list", "find");

  public AutoCloseableResourceInspection() {
    myMethodMatcher = new MethodMatcher()
      .add("java.util.Formatter", "format")
      .add("java.io.Writer", "append")
      .add("com.google.common.base.Preconditions", "checkNotNull")
      .add("org.hibernate.Session", "close")
      .add("java.io.PrintWriter", "printf")
      .add("java.io.PrintStream", "printf")
      .finishDefault();
  }

  /**
   * Warning! This class has to manually save settings to xml using its {@code readSettings()} and {@code writeSettings()} methods
   */
  @NotNull
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    final ListTable table =
      new ListTable(new ListWrappingTableModel(ignoredTypes, InspectionGadgetsBundle.message("ignored.autocloseable.types.column.label")));
    final JPanel tablePanel =
      UiUtils.createAddRemoveTreeClassChooserPanel(
        InspectionGadgetsBundle.message("choose.autocloseable.type.to.ignore.title"),
        InspectionGadgetsBundle.message("ignored.autocloseable.types.label"),
        table,
        true,
        "java.lang.AutoCloseable");
    final ListTable table2 = new ListTable(
      new ListWrappingTableModel(Arrays.asList(myMethodMatcher.getClassNames(), myMethodMatcher.getMethodNamePatterns()),
                                 InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
                                 InspectionGadgetsBundle.message("method.name.regex")));
    table2.setEnabled(!ignoreFromMethodCall);
    final JPanel tablePanel2 =
      UI.PanelFactory.panel(UiUtils.createAddRemoveTreeClassChooserPanel(table2, JavaBundle.message("dialog.title.choose.class")))
        .withLabel(InspectionGadgetsBundle.message("inspection.autocloseable.resource.ignored.methods.title")).moveLabelOnTop()
        .resizeY(true).createPanel();
    panel.add(tablePanel, "growx, wrap");
    panel.add(tablePanel2, "growx, wrap");
    final CheckBox checkBox =
      new CheckBox(InspectionGadgetsBundle.message("auto.closeable.resource.returned.option"), this, "ignoreFromMethodCall");
    checkBox.addItemListener(e -> table2.setEnabled(e.getStateChange() == ItemEvent.DESELECTED));
    panel.add(checkBox, "growx, wrap");
    panel.addCheckbox(InspectionGadgetsBundle.message("any.method.may.close.resource.argument"), "anyMethodMayClose");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.constructor.method.references"), "ignoreConstructorMethodReferences");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.getters.returning.resource"), "ignoreGettersReturningResource");
    return ScrollPaneFactory.createScrollPane(panel);
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
      final @NonNls String name = option.getAttributeValue("name");
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
    writeBooleanOption(node, "ignoreConstructorMethodReferences", true);
    writeBooleanOption(node, "ignoreGettersReturningResource", true);
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
            || !TypeUtils.expressionHasTypeOrSubtype(expression, ignoredTypes)) &&
           (!ignoreGettersReturningResource || !isGetter(expression));
  }

  private static boolean isGetter(@NotNull PsiExpression expression) {
    PsiMethodCallExpression call = tryCast(expression, PsiMethodCallExpression.class);
    if (call == null) return false;
    String callName = call.getMethodExpression().getReferenceName();
    if (callName == null) return false;
    return callName.startsWith("get") && !callName.equals("getClass") && !callName.equals("getResourceAsStream");
  }

  @Override
  protected boolean canTakeOwnership(@NotNull PsiExpression expression) {
    return TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE);
  }

  private boolean isStreamHoldingResource(PsiExpression expression) {
    return STREAM_HOLDING_RESOURCE.matches(tryCast(expression, PsiMethodCallExpression.class));
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel7OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AutoCloseableResourceVisitor();
  }

  private class AutoCloseableResourceFix extends InspectionGadgetsFix {
    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("auto.closeable.resource.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
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
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (isSafelyClosedResource(expression)) {
        return;
      }
      registerNewExpressionError(expression, expression.getType(), Boolean.FALSE);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (ignoreFromMethodCall || myMethodMatcher.matches(expression) || isSafelyClosedResource(expression)) {
        return;
      }
      if (isReturnedByContract(expression)) return;
      registerMethodCallError(expression, expression.getType(), !isStreamHoldingResource(expression));
    }

    private boolean isReturnedByContract(PsiMethodCallExpression expression) {
      PsiExpression returnedValue = JavaMethodContractUtil.findReturnedValue(expression);
      PsiExpression[] arguments = expression.getArgumentList().getExpressions();
      PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
      if (returnedValue != null && qualifier == returnedValue) return true;
      return ArrayUtil.indexOfIdentity(arguments, returnedValue) != -1;
    }

    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      if (ignoreConstructorMethodReferences) return;
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
      if (variable instanceof PsiResourceVariable || isResourceEscaping(variable, expression)) return true;
      if (variable == null) return false;
      return ContainerUtil.or(ImplicitResourceCloser.EP_NAME.getExtensionList(), closer -> closer.isSafelyClosed(variable));
    }
  }
}
