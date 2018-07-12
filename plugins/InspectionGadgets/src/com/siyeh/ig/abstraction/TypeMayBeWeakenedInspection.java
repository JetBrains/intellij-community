/*
 * Copyright 2006-2018 Bas Leijdekkers
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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
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
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Query;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.WeakestTypeFinder;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class TypeMayBeWeakenedInspection extends AbstractBaseJavaLocalInspectionTool {
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

  class AddStopWordQuickfix implements LowPriorityAction, LocalQuickFix {
    private final List<String> myCandidates;

    AddStopWordQuickfix(@NotNull List<String> candidates) {
      myCandidates = candidates;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      if (myCandidates.size() == 1) {
        InspectionGadgetsBundle.message("inspection.type.may.be.weakened.add.stopper.single", myCandidates.get(0));
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
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      if (myCandidates.size() == 1) {
        addClass(myCandidates.get(0), descriptor.getPsiElement());
        return;
      }
      Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor == null) return;
      String hint = InspectionGadgetsBundle.message("inspection.type.may.be.weakened.add.stop.class.selection.popup");
      ListPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<String>(hint, myCandidates) {
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
    List<Element> options = node.getChildren("option");
    Map<String, String> values = new HashMap<>();
    for (Element option : options) {
      Attribute nameAttribute = option.getAttribute("name");
      if (nameAttribute == null) continue;
      Attribute valueAttribute = option.getAttribute("value");
      if (valueAttribute == null) continue;
      values.put(nameAttribute.getValue(), valueAttribute.getValue());
    }
    useRighthandTypeAsWeakestTypeInAssignments = readOrDefault(values, "useRighthandTypeAsWeakestTypeInAssignments", useRighthandTypeAsWeakestTypeInAssignments);
    useParameterizedTypeForCollectionMethods = readOrDefault(values, "useParameterizedTypeForCollectionMethods", useParameterizedTypeForCollectionMethods);
    doNotWeakenToJavaLangObject = readOrDefault(values, "doNotWeakenToJavaLangObject", doNotWeakenToJavaLangObject);
    onlyWeakentoInterface = readOrDefault(values, "onlyWeakentoInterface", onlyWeakentoInterface);
    doNotWeakenReturnType = readOrDefault(values, "doNotWeakenReturnType", doNotWeakenReturnType);
    doNotWeakenInferredVariableType = readOrDefault(values, "doNotWeakenInferredVariableType", doNotWeakenInferredVariableType);
    readStopClasses(node);
  }

  private static boolean readOrDefault(@NotNull Map<String, String> options, @NotNull String name, boolean defaultValue) {
    String value = options.get(name);
    if (value == null) return defaultValue;
    return Boolean.parseBoolean(value);
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
    writeBool(node, useRighthandTypeAsWeakestTypeInAssignments, "useRighthandTypeAsWeakestTypeInAssignments");
    writeBool(node, useParameterizedTypeForCollectionMethods, "useParameterizedTypeForCollectionMethods");
    writeBool(node, doNotWeakenToJavaLangObject, "doNotWeakenToJavaLangObject");
    writeBool(node, onlyWeakentoInterface, "onlyWeakentoInterface");
    if (!doNotWeakenReturnType) {
      writeBool(node, false, "doNotWeakenReturnType");
    }
    if (doNotWeakenInferredVariableType) {
      writeBool(node, true, "doNotWeakenInferredVariableType");
    }
    if (!myStopClassSet.isEmpty()) {
      Element stopClasses = new Element("stopClasses");
      stopClasses.addContent(String.join(",", myStopClassSet));
      node.addContent(stopClasses);
    }
  }

  private static void writeBool(@NotNull Element node, boolean value, @NotNull String name) {
    Element optionElement = new Element("option");
    optionElement.setAttribute("name", name);
    optionElement.setAttribute("value", String.valueOf(value));
    node.addContent(optionElement);
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

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("inspection.type.may.be.weakened.display.name");
  }

  private static String getClassName(@NotNull PsiClass aClass) {
    final String qualifiedName = aClass.getQualifiedName();
    return qualifiedName == null ? aClass.getName() : qualifiedName;
  }

  @Override
  @NotNull
  public JComponent createOptionsPanel() {
    VerticalBox verticalBox = new VerticalBox();
    JBScrollPane scrollPane = new JBScrollPane(verticalBox, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
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
    verticalBox.add(optionsPanel);
    final ListTable stopClassesTable = new ListTable(myStopClassesModel);

    final JPanel stopClassesPanel =
      UiUtils.createAddRemoveTreeClassChooserPanel(stopClassesTable, InspectionGadgetsBundle
        .message("inspection.type.may.be.weakened.add.stop.class.selection.table"), CommonClassNames.JAVA_LANG_OBJECT);
    verticalBox.add(stopClassesPanel);
    return scrollPane;
  }

  private static class TypeMayBeWeakenedFix implements LocalQuickFix {
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
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
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
        replacement = typeElement.replace(newTypeElement);
      } else {
        final PsiJavaCodeReferenceElement referenceElement = factory.createReferenceElementByType(classType);
        replacement = componentReferenceElement.replace(referenceElement);
      }
      final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      javaCodeStyleManager.shortenClassReferences(replacement);

    }
  }


  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new TypeMayBeWeakenedVisitor(holder, isOnTheFly);
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

  private class TypeMayBeWeakenedVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;

    TypeMayBeWeakenedVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      myHolder = holder;
      myIsOnTheFly = isOnTheFly;
    }

    @Override
    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      if (variable instanceof PsiParameter) {
        final PsiParameter parameter = (PsiParameter)variable;
        final PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiCatchSection) {
          // do not weaken catch block parameters
          return;
        }
        else if (declarationScope instanceof PsiLambdaExpression && parameter.getTypeElement() == null) {
          //no need to check inferred lambda params
          return;
        }
        else if (declarationScope instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)declarationScope;
          final PsiClass containingClass = method.getContainingClass();
          if (containingClass == null ||
              containingClass.isInterface()) {
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
      if (myIsOnTheFly && variable instanceof PsiField) {
        // checking variables with greater visibility is too expensive
        // for error checking in the editor
        if (!variable.hasModifierProperty(PsiModifier.PRIVATE)) {
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
        if (method == null || UnusedSymbolUtil.isImplicitUsage(variable.getProject(), method, null)) return;
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
      PsiIdentifier nameIdentifier = variable.getNameIdentifier();
      if (nameIdentifier == null) return;
      registerProblem(nameIdentifier, variable, originClass, weakestClasses);
    }


    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (doNotWeakenReturnType) return;
      if (myIsOnTheFly && !method.hasModifierProperty(PsiModifier.PRIVATE) && !ApplicationManager.getApplication().isUnitTestMode()) {
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
      PsiIdentifier identifier = method.getNameIdentifier();
      if (identifier == null) return;
      registerProblem(identifier, method, originClass, weakestClasses);
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

      weakestClasses = weakestClasses.stream()
                                     .map(psiClass -> tryReplaceWithParentStopper(originClass, psiClass, myStopClassSet))
                                     .collect(Collectors.toList());
      return weakestClasses;
    }

    private void registerProblem(@NotNull PsiElement psiElement,
                                 @NotNull PsiElement element,
                                 @NotNull PsiClass originalClass,
                                 @NotNull Collection<PsiClass> weakerClasses) {
      final Collection<LocalQuickFix> fixes = new ArrayList<>();
      if (element instanceof PsiVariable && !doNotWeakenInferredVariableType) {
        PsiTypeElement typeElement = ((PsiVariable)element).getTypeElement();
        if (typeElement != null && typeElement.isInferredType()) {
          fixes.add(new SetInspectionOptionFix(TypeMayBeWeakenedInspection.this, "doNotWeakenInferredVariableType", InspectionGadgetsBundle
            .message("inspection.type.may.be.weakened.do.not.weaken.inferred.variable.type"), true));
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
        if (!candidates.isEmpty() && (myIsOnTheFly || candidates.size() == 1)) {
          fixes.add(new AddStopWordQuickfix(candidates)); // not this class name, but all superclass names excluding this
        }
      }
      myHolder.registerProblem(psiElement, getDescription(element, weakerClasses), fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }

    @NotNull
    private String getDescription(@NotNull PsiElement element, @NotNull Collection<PsiClass> weakerClasses) {
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
}