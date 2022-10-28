// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.daemon.impl.quickfix.ImplementOrExtendFix;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.quickfix.CreateConstructorFix;
import org.jetbrains.idea.devkit.util.ActionType;
import org.jetbrains.idea.devkit.util.ComponentType;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.List;
import java.util.Set;

public class RegistrationProblemsInspection extends DevKitInspectionBase {

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "ComponentRegistrationProblems";
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull PsiClass checkedClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiIdentifier nameIdentifier = checkedClass.getNameIdentifier();
    if (nameIdentifier != null &&
        checkedClass.getQualifiedName() != null &&
        checkedClass.getContainingFile().getVirtualFile() != null &&
        !checkedClass.isInterface() &&
        !checkedClass.isEnum() &&
        !checkedClass.hasModifierProperty(PsiModifier.PRIVATE) &&
        !checkedClass.hasModifierProperty(PsiModifier.PROTECTED) &&
        !PsiUtil.isInnerClass(checkedClass)) {
      final RegistrationCheckerUtil.RegistrationType registrationType = RegistrationCheckerUtil.RegistrationType.ALL;
      final Set<PsiClass> componentClasses = RegistrationCheckerUtil.getRegistrationTypes(checkedClass, registrationType);
      if (componentClasses != null && !componentClasses.isEmpty()) {
        List<ProblemDescriptor> problems = new SmartList<>();

        for (PsiClass componentClass : componentClasses) {
          if (ActionType.ACTION.myClassName.equals(componentClass.getQualifiedName()) &&
              !checkedClass.isInheritor(componentClass, true)) {
            problems.add(manager.createProblemDescriptor(nameIdentifier,
                                                         DevKitBundle.message("inspections.registration.problems.incompatible.message",
                                                                              componentClass.getQualifiedName()), isOnTheFly,
                                                         ImplementOrExtendFix.createFixes(nameIdentifier, componentClass, checkedClass,
                                                                                          isOnTheFly),
                                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
        if (ActionType.ACTION.isOfType(checkedClass)) {
          if (ConstructorType.getNoArgCtor(checkedClass) == null) {
            problems.add(manager.createProblemDescriptor(nameIdentifier,
                                                         DevKitBundle.message("inspections.registration.problems.missing.noarg.ctor"),
                                                         new CreateConstructorFix(checkedClass, isOnTheFly),
                                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly));
          }
        }
        if (checkedClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          problems.add(manager.createProblemDescriptor(nameIdentifier,
                                                       DevKitBundle.message("inspections.registration.problems.abstract"), isOnTheFly,
                                                       LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
      }
    }
    return null;
  }

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (DescriptorUtil.isPluginXml(file)) {
      final XmlDocument document = ((XmlFile)file).getDocument();
      if (document == null) {
        return null;
      }

      final XmlTag rootTag = document.getRootTag();
      assert rootTag != null;

      final RegistrationChecker checker = new RegistrationChecker(manager, (XmlFile)file, isOnTheFly);
      DescriptorUtil.processComponents(rootTag, checker);
      DescriptorUtil.processActions(rootTag, checker);
      return checker.getProblems();
    }
    return null;
  }

  private static final class RegistrationChecker implements ComponentType.Processor, ActionType.Processor {
    private List<ProblemDescriptor> myList;
    private final InspectionManager myManager;
    private final PsiManager myPsiManager;
    private final GlobalSearchScope myScope;
    private final MultiMap<ComponentType, String> myInterfaceClasses = MultiMap.createSet();
    private final boolean myOnTheFly;

    private RegistrationChecker(InspectionManager manager, XmlFile xmlFile, boolean onTheFly) {
      myManager = manager;
      myOnTheFly = onTheFly;
      myPsiManager = xmlFile.getManager();
      myScope = xmlFile.getResolveScope();
    }

    //<editor-fold desc="Components">
    @Override
    public boolean process(ComponentType type, XmlTag component, @Nullable XmlTagValue impl, @Nullable XmlTagValue intf) {
      if (impl != null) {
        String intfName = null;
        PsiClass intfClass = null;
        if (intf != null) {
          intfName = intf.getTrimmedText();
          intfClass = findClass(intfName);
        }
        final String implClassName = impl.getTrimmedText();
        final PsiClass implClass = findClass(implClassName);
        if (implClass != null) {
          if (implClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            addProblem(impl, DevKitBundle.message("inspections.registration.problems.abstract"), myOnTheFly);
          }
        }
        if (intfName != null && intfClass != null && implClass != null) {
          final String fqn = intfClass.getQualifiedName();
          if (type == ComponentType.MODULE) {
            if (!checkInterface(type, fqn, intf)) {
              // module components can be restricted to modules of certain types
              final String[] keys = makeQualifiedModuleInterfaceNames(component, fqn);
              for (String key : keys) {
                checkInterface(type, key, intf);
                myInterfaceClasses.putValue(type, key);
              }
            }
          }
          else {
            checkInterface(type, fqn, intf);
            myInterfaceClasses.putValue(type, fqn);
          }
          if (intfClass != implClass && !implClass.isInheritor(intfClass, true)) {
            addProblem(impl, DevKitBundle.message("inspections.registration.problems.component.incompatible.interface", fqn), myOnTheFly);
          }
        }
      }
      return true;
    }

    private void addProblem(XmlTagValue impl, @InspectionMessage String problem, boolean isOnTheFly, LocalQuickFix... fixes) {
      final XmlText[] textElements = impl.getTextElements();
      for (XmlText text : textElements) {
        if (text.getValue().trim().length() > 0) {
          addProblem(text, problem, isOnTheFly, fixes);
        }
      }
    }

    private boolean checkInterface(ComponentType type, String fqn, XmlTagValue value) {
      if (myInterfaceClasses.get(type).contains(fqn)) {
        addProblem(value, DevKitBundle.message("inspections.registration.problems.component.duplicate.interface", fqn), myOnTheFly);
        return true;
      }
      return false;
    }

    private static String[] makeQualifiedModuleInterfaceNames(XmlTag component, String fqn) {
      final XmlTag[] children = component.findSubTags("option");
      for (XmlTag child : children) {
        if ("type".equals(child.getAttributeValue("name"))) {
          final String value = child.getAttributeValue("value");
          final SmartList<String> names = new SmartList<>();
          if (value != null) {
            final String[] moduleTypes = value.split(";");
            for (String moduleType : moduleTypes) {
              names.add(fqn + "#" + moduleType);
            }
          }
          return ArrayUtilRt.toStringArray(names);
        }
      }
      return new String[]{fqn};
    }
    //</editor-fold>

    //<editor-fold desc="Actions">
    @Override
    public boolean process(ActionType type, XmlTag action) {
      final XmlAttribute attribute = action.getAttribute("class");
      if (attribute != null) {
        final PsiElement token = getAttValueToken(attribute);
        if (token != null) {
          String attributeValue = attribute.getValue();
          if (attributeValue != null) {
            final String actionClassName = attributeValue.trim();
            final PsiClass actionClass = findClass(actionClassName);
            if (actionClass != null) {
              if (actionClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                addProblem(token, DevKitBundle.message("inspections.registration.problems.abstract"), myOnTheFly);
              }
            }
          }
        }
      }
      return true;
    }

    @Nullable
    private static PsiElement getAttValueToken(@NotNull XmlAttribute attribute) {
      final XmlAttributeValue valueElement = attribute.getValueElement();
      if (valueElement == null) return null;

      final PsiElement[] children = valueElement.getChildren();
      if (children.length == 3 && children[1] instanceof XmlToken) {
        return children[1];
      }
      if (children.length == 1 && children[0] instanceof PsiErrorElement) return null;
      return valueElement;
    }
    //</editor-fold>

    private void addProblem(PsiElement element, @InspectionMessage String problem, boolean onTheFly, LocalQuickFix... fixes) {
      if (myList == null) myList = new SmartList<>();
      myList.add(myManager.createProblemDescriptor(element, problem, onTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
    }

    @Nullable
    private PsiClass findClass(@NotNull String fqn) {
      return ClassUtil.findPsiClass(myPsiManager, fqn, null, true, myScope);
    }

    public ProblemDescriptor @Nullable [] getProblems() {
      return myList != null ? myList.toArray(ProblemDescriptor.EMPTY_ARRAY) : null;
    }
  }

  static class ConstructorType {
    static final ConstructorType DEFAULT = new ConstructorType();
    final PsiMethod myCtor;

    private ConstructorType() {
      myCtor = null;
    }

    protected ConstructorType(PsiMethod ctor) {
      assert ctor != null;
      myCtor = ctor;
    }

    public static ConstructorType getNoArgCtor(PsiClass checkedClass) {
      final PsiMethod[] constructors = checkedClass.getConstructors();
      if (constructors.length > 0) {
        for (PsiMethod ctor : constructors) {
          if (ctor.getParameterList().isEmpty()) {
            return new ConstructorType(ctor);
          }
        }
        return null;
      }
      return DEFAULT;
    }
  }
}
