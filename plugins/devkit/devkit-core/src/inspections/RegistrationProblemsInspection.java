// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.actionSystem.AnAction;
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
import org.jetbrains.idea.devkit.inspections.quickfix.ImplementOrExtendFix;
import org.jetbrains.idea.devkit.util.ActionType;
import org.jetbrains.idea.devkit.util.ComponentType;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.List;
import java.util.Set;

/**
 * @author swr
 */
public class RegistrationProblemsInspection extends DevKitInspectionBase {

  public boolean CHECK_PLUGIN_XML = true;
  public boolean CHECK_JAVA_CODE = true;
  public boolean CHECK_ACTIONS = true;

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "ComponentRegistrationProblems";
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel jPanel = new JPanel();
    jPanel.setLayout(new BoxLayout(jPanel, BoxLayout.Y_AXIS));

    final JCheckBox checkPluginXml = new JCheckBox(
            DevKitBundle.message("inspections.registration.problems.option.check.plugin.xml"),
            CHECK_PLUGIN_XML);
    checkPluginXml.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        CHECK_PLUGIN_XML = checkPluginXml.isSelected();
      }
    });

    final JCheckBox checkJavaActions = new JCheckBox(
            DevKitBundle.message("inspections.registration.problems.option.check.java.actions"),
            CHECK_ACTIONS);
    checkJavaActions.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        CHECK_ACTIONS = checkJavaActions.isSelected();
      }
    });

    final JCheckBox checkJavaCode = new JCheckBox(
            DevKitBundle.message("inspections.registration.problems.option.check.java.code"),
            CHECK_JAVA_CODE);
    checkJavaCode.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        final boolean selected = checkJavaCode.isSelected();
        CHECK_JAVA_CODE = selected;
        checkJavaActions.setEnabled(selected);
      }
    });

    jPanel.add(checkPluginXml);
    jPanel.add(checkJavaCode);
    jPanel.add(checkJavaActions);
    return jPanel;
  }

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (CHECK_PLUGIN_XML && DescriptorUtil.isPluginXml(file)) {
      return checkPluginXml((XmlFile)file, manager, isOnTheFly);
    }
    return null;
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull PsiClass checkedClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiIdentifier nameIdentifier = checkedClass.getNameIdentifier();

    if (CHECK_JAVA_CODE &&
        nameIdentifier != null &&
        checkedClass.getQualifiedName() != null &&
        checkedClass.getContainingFile().getVirtualFile() != null &&
        !checkedClass.isInterface() &&
        !checkedClass.isEnum() &&
        !checkedClass.hasModifierProperty(PsiModifier.PRIVATE) &&
        !checkedClass.hasModifierProperty(PsiModifier.PROTECTED) &&
        !PsiUtil.isInnerClass(checkedClass) ) {
      final RegistrationCheckerUtil.RegistrationType registrationType =
        CHECK_ACTIONS ? RegistrationCheckerUtil.RegistrationType.ALL : RegistrationCheckerUtil.RegistrationType.ALL_COMPONENTS;
      final Set<PsiClass> componentClasses = RegistrationCheckerUtil.getRegistrationTypes(checkedClass, registrationType);
      if (componentClasses != null && !componentClasses.isEmpty()) {
        List<ProblemDescriptor> problems = new SmartList<>();

        for (PsiClass compClass : componentClasses) {
          if (ActionType.ACTION.myClassName.equals(compClass.getQualifiedName()) &&
              !checkedClass.isInheritor(compClass, true)) {
            problems.add(manager.createProblemDescriptor(nameIdentifier,
                    DevKitBundle.message("inspections.registration.problems.incompatible.message",
                            compClass.isInterface() ?
                                    DevKitBundle.message("keyword.implement") :
                                    DevKitBundle.message("keyword.extend"),
                            compClass.getQualifiedName()), isOnTheFly, ImplementOrExtendFix.createFix(compClass, checkedClass, isOnTheFly),
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
        if (isAbstract(checkedClass)) {
          problems.add(manager.createProblemDescriptor(nameIdentifier,
                                                       DevKitBundle.message("inspections.registration.problems.abstract"), isOnTheFly,
                                                       LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
      }
    }
    return null;
  }

  private static ProblemDescriptor @Nullable [] checkPluginXml(XmlFile xmlFile, InspectionManager manager, boolean isOnTheFly) {
    final XmlDocument document = xmlFile.getDocument();
    if (document == null) {
      return null;
    }

    final XmlTag rootTag = document.getRootTag();
    assert rootTag != null;

    final RegistrationChecker checker = new RegistrationChecker(manager, xmlFile, isOnTheFly);

    DescriptorUtil.processComponents(rootTag, checker);

    DescriptorUtil.processActions(rootTag, checker);

    return checker.getProblems();
  }

  private static boolean isAbstract(PsiModifierListOwner checkedClass) {
    return checkedClass.hasModifierProperty(PsiModifier.ABSTRACT);
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

  private static class RegistrationChecker implements ComponentType.Processor, ActionType.Processor {
    private List<ProblemDescriptor> myList;
    private final InspectionManager myManager;
    private final XmlFile myXmlFile;
    private final PsiManager myPsiManager;
    private final GlobalSearchScope myScope;
    private final MultiMap<ComponentType, String> myInterfaceClasses = MultiMap.createSet();
    private final boolean myOnTheFly;

    private RegistrationChecker(InspectionManager manager, XmlFile xmlFile, boolean onTheFly) {
      myManager = manager;
      myXmlFile = xmlFile;
      myOnTheFly = onTheFly;
      myPsiManager = xmlFile.getManager();
      myScope = xmlFile.getResolveScope();
    }

    @Nullable
    private PsiClass findClass(@NotNull String fqn) {
      return ClassUtil.findPsiClass(myPsiManager, fqn, null, true, myScope);
    }

    @Override
    public boolean process(ComponentType type, XmlTag component, @Nullable XmlTagValue impl, @Nullable XmlTagValue intf) {
      if (impl == null) {
        addProblem(component,
                DevKitBundle.message("inspections.registration.problems.missing.implementation.class"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly);
      } else {
        String intfName = null;
        PsiClass intfClass = null;
        if (intf != null) {
          intfName = intf.getTrimmedText();
          intfClass = findClass(intfName);
        }
        final String implClassName = impl.getTrimmedText();
        final PsiClass implClass = findClass(implClassName);
        if (implClass == null) {
          addProblem(impl,
                  DevKitBundle.message("inspections.registration.problems.cannot.resolve.class",
                          DevKitBundle.message("class.implementation")),
                  ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, myOnTheFly, ((LocalQuickFix)QuickFixFactory.getInstance()
              .createCreateClassOrInterfaceFix(myXmlFile, implClassName, true, intfClass != null ? intfName : type.myClassName)));
        } else {
          if (isAbstract(implClass)) {
            addProblem(impl,
                    DevKitBundle.message("inspections.registration.problems.abstract"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly);
          }
        }
        if (intfName != null) {
          if (intfClass == null) {
            addProblem(intf,
                    DevKitBundle.message("inspections.registration.problems.cannot.resolve.class",
                            DevKitBundle.message("class.interface")),
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, myOnTheFly, ((LocalQuickFix)QuickFixFactory.getInstance()
              .createCreateClassOrInterfaceFix(myXmlFile, intfName, false, type.myClassName)),
                    ((LocalQuickFix)QuickFixFactory.getInstance()
              .createCreateClassOrInterfaceFix(myXmlFile, intfName, true, type.myClassName)));
          } else if (implClass != null) {
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
            } else {
              checkInterface(type, fqn, intf);
              myInterfaceClasses.putValue(type, fqn);
            }

            if (intfClass != implClass && !implClass.isInheritor(intfClass, true)) {
              addProblem(impl,
                  DevKitBundle.message("inspections.registration.problems.component.incompatible.interface", fqn),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly);
            }
          }
        }
      }
      return true;
    }

    private boolean checkInterface(ComponentType type, String fqn, XmlTagValue value) {
      if (myInterfaceClasses.get(type).contains(fqn)) {
        addProblem(value,
            DevKitBundle.message("inspections.registration.problems.component.duplicate.interface", fqn),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly);
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
      return new String[]{ fqn };
    }

    @Override
    public boolean process(ActionType type, XmlTag action) {
      final XmlAttribute attribute = action.getAttribute("class");
      if (attribute != null) {
        final PsiElement token = getAttValueToken(attribute);
        if (token != null) {
          final String actionClassName = attribute.getValue().trim();
          final PsiClass actionClass = findClass(actionClassName);
          if (actionClass == null) {
            addProblem(token,
                    DevKitBundle.message("inspections.registration.problems.cannot.resolve.class",
                            DevKitBundle.message("class.action")),
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, myOnTheFly, ((LocalQuickFix)QuickFixFactory.getInstance()
              .createCreateClassOrInterfaceFix(token, actionClassName, true, AnAction.class.getName())));
          } else {
            if (!type.isOfType(actionClass)) {
              final PsiClass psiClass = findClass(type.myClassName);
              if (psiClass != null && !actionClass.isInheritor(psiClass, true)) {
                addProblem(token,
                        DevKitBundle.message("inspections.registration.problems.action.incompatible.class", type.myClassName),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly, ImplementOrExtendFix.createFix(psiClass, actionClass, myOnTheFly));
              }
            }
            final ConstructorType noArgCtor = ConstructorType.getNoArgCtor(actionClass);
            if (noArgCtor == null) {
              addProblem(token,
                      DevKitBundle.message("inspections.registration.problems.missing.noarg.ctor"),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly, new CreateConstructorFix(actionClass, myOnTheFly));
            }
            if (isAbstract(actionClass)) {
              addProblem(token,
                      DevKitBundle.message("inspections.registration.problems.abstract"),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly);
            }
          }
        }
      }
      return true;
    }

    private void addProblem(XmlTagValue impl, String problem, ProblemHighlightType type, boolean isOnTheFly, LocalQuickFix... fixes) {
      final XmlText[] textElements = impl.getTextElements();
      for (XmlText text : textElements) {
        if (text.getValue().trim().length() > 0) {
          addProblem(text, problem, type, isOnTheFly, fixes);
        }
      }
    }

    private void addProblem(PsiElement element, String problem, ProblemHighlightType type, boolean onTheFly, LocalQuickFix... fixes) {
      if (myList == null) myList = new SmartList<>();
      myList.add(myManager.createProblemDescriptor(element, problem, onTheFly, fixes, type));
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
