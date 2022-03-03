// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SetInspectionOptionFix;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Query;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.WeakestTypeFinder;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class TypeMayBeWeakenedInspection extends BaseInspection {
  @SuppressWarnings({"PublicField", "WeakerAccess"})
  public boolean useRighthandTypeAsWeakestTypeInAssignments = true;

  @SuppressWarnings({"PublicField", "WeakerAccess"})
  public boolean useParameterizedTypeForCollectionMethods = true;

  @SuppressWarnings({"PublicField", "WeakerAccess"})
  public boolean doNotWeakenToJavaLangObject = true;

  @SuppressWarnings("PublicField")
  public boolean onlyWeakentoInterface = true;

  @SuppressWarnings("PublicField")
  public boolean doNotWeakenReturnType = true;

  @SuppressWarnings({"PublicField", "WeakerAccess"})
  public boolean doNotWeakenInferredVariableType;

  public OrderedSet<String> myStopClassSet = new OrderedSet<>();

  private final ListWrappingTableModel myStopClassesModel =
    new ListWrappingTableModel(myStopClassSet,
                               InspectionGadgetsBundle.message("inspection.type.may.be.weakened.add.stop.class.selection.table"));

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final PsiElement element = (PsiElement)infos[0];
    @SuppressWarnings("unchecked") final Collection<PsiClass> weakerClasses = (Collection<PsiClass>)infos[1];
    @NonNls final StringBuilder builder = new StringBuilder();
    final Iterator<PsiClass> iterator = weakerClasses.iterator();
    if (iterator.hasNext()) {
      builder.append('\'').append(getClassName(iterator.next())).append('\'');
      while (iterator.hasNext()) {
        builder.append(", '").append(getClassName(iterator.next())).append('\'');
      }
    }
    if (element instanceof PsiField) {
      return InspectionGadgetsBundle.message("inspection.type.may.be.weakened.field.problem.descriptor",
                                             builder.toString());
    }
    if (element instanceof PsiParameter) {
      return InspectionGadgetsBundle.message("inspection.type.may.be.weakened.parameter.problem.descriptor",
                                             builder.toString());
    }
    if (element instanceof PsiMethod) {
      return InspectionGadgetsBundle.message("inspection.type.may.be.weakened.method.problem.descriptor",
                                             builder.toString());
    }
    return InspectionGadgetsBundle.message("inspection.type.may.be.weakened.problem.descriptor", builder.toString());
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    final PsiElement element = (PsiElement)infos[0];
    @SuppressWarnings("unchecked") final Collection<PsiClass> weakerClasses = (Collection<PsiClass>)infos[1];
    final PsiClass originalClass = (PsiClass)infos[2];
    final boolean onTheFly = (boolean)infos[3];
    final List<InspectionGadgetsFix> fixes = new SmartList<>();

    if (element instanceof PsiVariable && !doNotWeakenInferredVariableType) {
      PsiTypeElement typeElement = ((PsiVariable)element).getTypeElement();
      if (typeElement != null && typeElement.isInferredType()) {
        final String optionText = InspectionGadgetsBundle.message("inspection.type.may.be.weakened.do.not.weaken.inferred.variable.type");
        fixes.add(new DelegatingFix(new SetInspectionOptionFix(this, "doNotWeakenInferredVariableType", optionText, true)));
      }
    }
    for (PsiClass weakestClass : weakerClasses) {
      final String className = getClassName(weakestClass);
      if (className == null) {
        continue;
      }
      fixes.add(new TypeMayBeWeakenedFix(className));
      List<String> candidates = getInheritors(originalClass, weakestClass);
      candidates.removeAll(myStopClassSet);
      if (!candidates.isEmpty() && (onTheFly || candidates.size() == 1)) {
        fixes.add(new AddStopWordQuickfix(candidates)); // not this class name, but all superclass names excluding this
      }
    }
    return fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  @NotNull
  private static List<String> getInheritors(@NotNull PsiClass from, @NotNull PsiClass to) {
    List<String> candidates = new ArrayList<>();
    String fromName = getClassName(from);
    if (fromName != null) {
      candidates.add(fromName);
    }
    for (PsiClass cls : InheritanceUtil.getSuperClasses(from)) {
      if (cls.isInheritor(to, true)) {
        String name = getClassName(cls);
        if (name == null) continue;
        candidates.add(name);
      }
    }
    return candidates;
  }

  class AddStopWordQuickfix extends InspectionGadgetsFix implements LowPriorityAction, LocalQuickFix {
    private final List<String> myCandidates;

    AddStopWordQuickfix(@NotNull List<String> candidates) {
      myCandidates = candidates;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      if (myCandidates.size() == 1) {
        return InspectionGadgetsBundle.message("inspection.type.may.be.weakened.add.stopper.single", myCandidates.get(0));
      }
      return InspectionGadgetsBundle.message("inspection.type.may.be.weakened.add.stopper");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.type.may.be.weakened.add.stop.class.family");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      if (myCandidates.size() == 1) {
        addClass(myCandidates.get(0), descriptor.getPsiElement());
        return;
      }
      Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor == null) return;
      String hint = InspectionGadgetsBundle.message("inspection.type.may.be.weakened.add.stop.class.selection.popup");
      ListPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(hint, myCandidates) {
        @Override
        public PopupStep onChosen(String selectedValue, boolean finalChoice) {
          CommandProcessor.getInstance().executeCommand(project, () -> addClass(selectedValue, descriptor.getPsiElement()),
                                                        InspectionGadgetsBundle.message("inspection.type.may.be.weakened.add.stopper"),
                                                        null);
          return super.onChosen(selectedValue, finalChoice);
        }
      });
      popup.showInBestPositionFor(editor);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  @Override
  public void readSettings(@NotNull Element node) {
    super.readSettings(node);
    readStopClasses(node);
  }

  private void readStopClasses(@NotNull Element node) {
    List<Element> classes = node.getChildren("stopClasses");
    if (classes.isEmpty()) return;
    Element element = classes.get(0);
    List<Content> contentList = element.getContent();
    if (contentList.isEmpty()) return;
    String text = contentList.get(0).getValue();
    myStopClassSet.addAll(Arrays.asList(text.split(",")));
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "doNotWeakenReturnType", "doNotWeakenInferredVariableType", "stopClasses");
    writeBooleanOption(node, "doNotWeakenReturnType", true);
    writeBooleanOption(node, "doNotWeakenInferredVariableType", false);
    if (!myStopClassSet.isEmpty()) {
      Element stopClasses = new Element("stopClasses");
      stopClasses.addContent(String.join(",", myStopClassSet));
      node.addContent(stopClasses);
    }
  }

  private void addClass(@NotNull String stopClass, @NotNull PsiElement context) {
    if (myStopClassSet.add(stopClass)) {
      final Project project = context.getProject();
      ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
      final VirtualFile vFile = PsiUtilCore.getVirtualFile(context);
      UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
        @Override
        public void undo() {
          myStopClassSet.remove(stopClass);
          ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
        }

        @Override
        public void redo() {
          myStopClassSet.add(stopClass);
          ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
        }

        @Override
        public boolean isGlobal() {
          return true;
        }
      });
    }
  }

  private static String getClassName(@NotNull PsiClass aClass) {
    final String qualifiedName = aClass.getQualifiedName();
    return qualifiedName == null ? aClass.getName() : qualifiedName;
  }

  @Override
  @NotNull
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);

    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.ignore.option"),
                             "useRighthandTypeAsWeakestTypeInAssignments");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.collection.method.option"),
                             "useParameterizedTypeForCollectionMethods");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.do.not.weaken.to.object.option"),
                             "doNotWeakenToJavaLangObject");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.only.weaken.to.an.interface"),
                             "onlyWeakentoInterface");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.do.not.weaken.return.type"),
                             "doNotWeakenReturnType");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.do.not.weaken.inferred.variable.type"),
                             "doNotWeakenInferredVariableType");

    final ListTable stopClassesTable = new ListTable(myStopClassesModel);
    final JPanel stopClassesPanel = UiUtils.createAddRemoveTreeClassChooserPanel(
      InspectionGadgetsBundle.message("inspection.type.may.be.weakened.add.stop.class.selection.table"),
      InspectionGadgetsBundle.message("inspection.type.may.be.weakened.add.stop.class.selection.table.label"),
      stopClassesTable,
      true,
      CommonClassNames.JAVA_LANG_OBJECT);
    optionsPanel.add(stopClassesPanel, "growx");

    return ScrollPaneFactory.createScrollPane(optionsPanel, true);
  }

  private static class TypeMayBeWeakenedFix extends InspectionGadgetsFix {
    private final String fqClassName;

    TypeMayBeWeakenedFix(@NotNull String fqClassName) {
      this.fqClassName = fqClassName;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", fqClassName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.type.may.be.weakened.weaken.type.family");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiTypeElement typeElement;
      if (parent instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)parent;
        typeElement = variable.getTypeElement();
      }
      else if (parent instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)parent;
        typeElement = method.getReturnTypeElement();
      }
      else {
        return;
      }
      if (typeElement == null) {
        return;
      }
      final PsiJavaCodeReferenceElement componentReferenceElement = typeElement.getInnermostComponentReferenceElement();
      boolean isInferredType = typeElement.isInferredType();
      if (componentReferenceElement == null && !isInferredType) {
        return;
      }
      final PsiType oldType = typeElement.getType();
      if (!(oldType instanceof PsiClassType)) {
        return;
      }
      final PsiClassType oldClassType = (PsiClassType)oldType;
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = facade.getElementFactory();
      final PsiType type = factory.createTypeFromText(fqClassName, element);
      if (!(type instanceof PsiClassType)) {
        return;
      }
      PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (aClass != null) {
        final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
        if (typeParameters.length != 0) {
          PsiClass newClass = classType.resolve();
          if (newClass == null) return;
          final Map<PsiTypeParameter, PsiType> typeParameterMap = new HashMap<>();
          for (int i = 0; i < typeParameters.length; i++) {
            final PsiTypeParameter typeParameter = typeParameters[i];
            final PsiType parameterType = PsiUtil.substituteTypeParameter(oldClassType, newClass, i, false);
            typeParameterMap.put(typeParameter, parameterType);
          }
          final PsiSubstitutor substitutor = factory.createSubstitutor(typeParameterMap);
          classType = factory.createType(aClass, substitutor);
        }
      }
      final PsiElement replacement;
      if (isInferredType) {
        PsiTypeElement newTypeElement = factory.createTypeElement(classType);
        replacement = new CommentTracker().replaceAndRestoreComments(typeElement, newTypeElement);
      }
      else {
        final PsiJavaCodeReferenceElement referenceElement = factory.createReferenceElementByType(classType);
        replacement = new CommentTracker().replaceAndRestoreComments(componentReferenceElement, referenceElement);
      }
      final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      javaCodeStyleManager.shortenClassReferences(replacement);

    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TypeMayBeWeakenedVisitor();
  }

  @NotNull
  private static PsiClass tryReplaceWithParentStopper(@NotNull PsiClass fromIncl,
                                                      @NotNull PsiClass toIncl,
                                                      @NotNull Collection<String> stopClasses) {
    for (PsiClass superClass : InheritanceUtil.getSuperClasses(fromIncl)) {
      if (!superClass.isInheritor(toIncl, true)) continue;
      if (stopClasses.contains(getClassName(superClass))) {
        return superClass;
      }
    }
    return toIncl;
  }

  private class TypeMayBeWeakenedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      if (variable instanceof PsiParameter) {
        final PsiParameter parameter = (PsiParameter)variable;
        if (parameter instanceof PsiPatternVariable) return;
        final PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiCatchSection) {
          // do not weaken catch block parameters
          return;
        }
        if (declarationScope instanceof PsiLambdaExpression && parameter.getTypeElement() == null) {
          //no need to check inferred lambda params
          return;
        }
        if (declarationScope instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)declarationScope;
          final PsiClass containingClass = method.getContainingClass();
          if (containingClass == null || containingClass.isInterface()) {
            return;
          }
          if (JavaHighlightUtil.isSerializationRelatedMethod(method, containingClass)) {
            return;
          }
          if (MethodUtils.hasSuper(method)) {
            // do not try to weaken parameters of methods with
            // super methods
            return;
          }
          final Query<PsiMethod> overridingSearch = OverridingMethodsSearch.search(method);
          if (overridingSearch.findFirst() != null) {
            // do not try to weaken parameters of methods with
            // overriding methods.
            return;
          }
        }
      }
      if (isOnTheFly() && variable instanceof PsiField) {
        // checking variables with greater visibility is too expensive
        // for error checking in the editor
        if (!variable.hasModifierProperty(PsiModifier.PRIVATE) || variable instanceof PsiEnumConstant) {
          return;
        }
      }
      if (doNotWeakenInferredVariableType) {
        PsiTypeElement typeElement = variable.getTypeElement();
        if (typeElement != null && typeElement.isInferredType()) {
          return;
        }
      }
      if (useRighthandTypeAsWeakestTypeInAssignments) {
        if (variable instanceof PsiParameter) {
          final PsiElement parent = variable.getParent();
          if (parent instanceof PsiForeachStatement) {
            final PsiForeachStatement foreachStatement = (PsiForeachStatement)parent;
            final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
            if (!(iteratedValue instanceof PsiNewExpression) && !(iteratedValue instanceof PsiTypeCastExpression)) {
              return;
            }
          }
        }
        else {
          final PsiExpression initializer = variable.getInitializer();
          if (!(initializer instanceof PsiNewExpression) && !(initializer instanceof PsiTypeCastExpression)) {
            return;
          }
        }
      }
      if (variable instanceof PsiParameter) {
        PsiMethod method = PsiTreeUtil.getParentOfType(variable, PsiMethod.class);
        if (method == null || UnusedSymbolUtil.isImplicitUsage(variable.getProject(), method)) return;
      }
      if (UnusedSymbolUtil.isImplicitWrite(variable) || UnusedSymbolUtil.isImplicitRead(variable)) {
        return;
      }
      PsiClassType classType = ObjectUtils.tryCast(variable.getType(), PsiClassType.class);
      if (classType == null) return;
      PsiClass originClass = classType.resolve();
      if (originClass == null) return;
      if (myStopClassSet.contains(getClassName(originClass))) return;
      Collection<PsiClass> weakestClasses = computeWeakestClasses(variable, originClass);
      if (weakestClasses.isEmpty()) {
        return;
      }
      registerVariableError(variable, variable, weakestClasses, originClass, isOnTheFly());
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (doNotWeakenReturnType || method instanceof PsiAnnotationMethod) return;
      if (isOnTheFly() && !method.hasModifierProperty(PsiModifier.PRIVATE) && !ApplicationManager.getApplication().isUnitTestMode()) {
        // checking methods with greater visibility is too expensive.
        // for error checking in the editor
        return;
      }
      if (MethodUtils.hasSuper(method)) {
        // do not try to weaken methods with super methods
        return;
      }
      final Query<PsiMethod> overridingSearch = OverridingMethodsSearch.search(method);
      if (overridingSearch.findFirst() != null) {
        // do not try to weaken methods with overriding methods.
        return;
      }
      PsiClassType classType = ObjectUtils.tryCast(method.getReturnType(), PsiClassType.class);
      if (classType == null) return;
      PsiClass originClass = classType.resolve();
      if (originClass == null) return;
      if (myStopClassSet.contains(getClassName(originClass))) return;
      Collection<PsiClass> weakestClasses = computeWeakestClasses(method, originClass);

      if (weakestClasses.isEmpty()) return;
      registerMethodError(method, method, weakestClasses, originClass, isOnTheFly());
    }

    @NotNull
    private Collection<PsiClass> computeWeakestClasses(@NotNull PsiElement element, @NotNull PsiClass originClass) {
      Collection<PsiClass> weakestClasses =
        WeakestTypeFinder.calculateWeakestClassesNecessary(element,
                                                           useRighthandTypeAsWeakestTypeInAssignments,
                                                           useParameterizedTypeForCollectionMethods);
      if (doNotWeakenToJavaLangObject) {
        weakestClasses.remove(ClassUtils.findObjectClass(element));
      }
      if (onlyWeakentoInterface) {
        weakestClasses.removeIf(weakestClass -> !weakestClass.isInterface());
      }

      weakestClasses = ContainerUtil.map(weakestClasses, psiClass -> tryReplaceWithParentStopper(originClass, psiClass, myStopClassSet));
      return weakestClasses;
    }
  }
}