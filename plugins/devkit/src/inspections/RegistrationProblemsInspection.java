/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.*;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.quickfix.CreateConstructorFix;
import org.jetbrains.idea.devkit.inspections.quickfix.ImplementOrExtendFix;
import org.jetbrains.idea.devkit.inspections.quickfix.MakePublicFix;
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
  private static final LocalQuickFix[] NO_FIX = new LocalQuickFix[0];

  public boolean CHECK_PLUGIN_XML = true;
  public boolean CHECK_JAVA_CODE = true;
  public boolean CHECK_ACTIONS = true;

  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public String getDisplayName() {
    return DevKitBundle.message("inspections.registration.problems.name");
  }

  @NonNls
  public String getShortName() {
    return "ComponentRegistrationProblems";
  }

  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel jPanel = new JPanel();
    jPanel.setLayout(new BoxLayout(jPanel, BoxLayout.Y_AXIS));

    final JCheckBox checkPluginXml = new JCheckBox(
            DevKitBundle.message("inspections.registration.problems.option.check.plugin.xml"),
            CHECK_PLUGIN_XML);
    checkPluginXml.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        CHECK_PLUGIN_XML = checkPluginXml.isSelected();
      }
    });

    final JCheckBox checkJavaActions = new JCheckBox(
            DevKitBundle.message("inspections.registration.problems.option.check.java.actions"),
            CHECK_ACTIONS);
    checkJavaActions.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        CHECK_ACTIONS = checkJavaActions.isSelected();
      }
    });

    final JCheckBox checkJavaCode = new JCheckBox(
            DevKitBundle.message("inspections.registration.problems.option.check.java.code"),
            CHECK_JAVA_CODE);
    checkJavaCode.addChangeListener(new ChangeListener() {
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

  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
    if (CHECK_PLUGIN_XML && isPluginXml(file)) {
      return checkPluginXml((XmlFile)file, manager, isOnTheFly);
    }
    return null;
  }

  @Nullable
  public ProblemDescriptor[] checkClass(PsiClass checkedClass, InspectionManager manager, boolean isOnTheFly) {
    final PsiIdentifier nameIdentifier = checkedClass.getNameIdentifier();

    if (CHECK_JAVA_CODE &&
            nameIdentifier != null &&
            checkedClass.getQualifiedName() != null &&
            checkedClass.getContainingFile().getVirtualFile() != null)
    {
      final Set<PsiClass> componentClasses = getRegistrationTypes(checkedClass, CHECK_ACTIONS);
      if (componentClasses != null) {
        List<ProblemDescriptor> problems = null;

        for (PsiClass compClass : componentClasses) {
          if (!checkedClass.isInheritor(compClass, true)) {
            problems = addProblem(problems, manager.createProblemDescriptor(nameIdentifier,
                    DevKitBundle.message("inspections.registration.problems.incompatible.message",
                            compClass.isInterface() ?
                                    DevKitBundle.message("keyword.implement") :
                                    DevKitBundle.message("keyword.extend"),
                            compClass.getQualifiedName()),
                    ImplementOrExtendFix.createFix(compClass, checkedClass, isOnTheFly),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
        if (ActionType.ACTION.isOfType(checkedClass)) {
          if (!isPublic(checkedClass)) {
            problems = addProblem(problems, manager.createProblemDescriptor(nameIdentifier,
                    DevKitBundle.message("inspections.registration.problems.not.public"),
                    new MakePublicFix(checkedClass, isOnTheFly),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }

          if (ConstructorType.getNoArgCtor(checkedClass) == null) {
            problems = addProblem(problems, manager.createProblemDescriptor(nameIdentifier,
                    DevKitBundle.message("inspections.registration.problems.missing.noarg.ctor"),
                    new CreateConstructorFix(checkedClass, isOnTheFly),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
        if (isAbstract(checkedClass)) {
          problems = addProblem(problems, manager.createProblemDescriptor(nameIdentifier,
                  DevKitBundle.message("inspections.registration.problems.abstract"),
                  NO_FIX, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
        return problems != null ? problems.toArray(new ProblemDescriptor[problems.size()]) : null;
      }
    }
    return null;
  }

  @Nullable
  public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    if (CHECK_ACTIONS && CHECK_JAVA_CODE &&
            method.isConstructor() &&
            method.getNameIdentifier() != null &&
            method.getContainingFile().getVirtualFile() != null)
    {
      if (method.getParameterList().getParametersCount() == 0 && !isPublic(method)) {
        final PsiClass checkedClass = method.getContainingClass();
        if (ActionType.ACTION.isOfType(checkedClass)) {
          if (isActionRegistered(checkedClass)) {
            return new ProblemDescriptor[]{
                    manager.createProblemDescriptor(method.getNameIdentifier(),
                            DevKitBundle.message("inspections.registration.problems.ctor.not.public"),
                            new MakePublicFix(method, isOnTheFly),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            };
          }
        }
      }
    }
    return null;
  }

  private List<ProblemDescriptor> addProblem(List<ProblemDescriptor> problems, ProblemDescriptor problemDescriptor) {
    if (problems == null) problems = new SmartList<ProblemDescriptor>();
    problems.add(problemDescriptor);
    return problems;
  }

  @Nullable
  private ProblemDescriptor[] checkPluginXml(XmlFile xmlFile, InspectionManager manager, boolean isOnTheFly) {
    final XmlTag rootTag = xmlFile.getDocument().getRootTag();
    assert rootTag != null;

    final RegistrationChecker checker = new RegistrationChecker(manager, xmlFile, isOnTheFly);

    DescriptorUtil.processComponents(rootTag, checker);

    DescriptorUtil.processActions(rootTag, checker);

    return checker.getProblems();
  }

  static class RegistrationChecker implements ComponentType.Processor, ActionType.Processor {
    private List<ProblemDescriptor> myList;
    private final InspectionManager myManager;
    private final boolean myOnTheFly;
    private final PsiManager myPsiManager;
    private final GlobalSearchScope myScope;
    private final Set<PsiClass> myInterfaceClasses = new THashSet<PsiClass>();

    public RegistrationChecker(InspectionManager manager, XmlFile xmlFile, boolean onTheFly) {
      myManager = manager;
      myOnTheFly = onTheFly;
      myPsiManager = xmlFile.getManager();
      myScope = xmlFile.getResolveScope();
    }

    public boolean process(ComponentType type, XmlTag component, @Nullable XmlTagValue impl, @Nullable XmlTagValue intf) {
      if (impl == null) {
        addProblem(component,
                DevKitBundle.message("inspections.registration.problems.missing.implementation.class"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      } else {
        final PsiClass implClass = myPsiManager.findClass(impl.getTrimmedText(), myScope);
        if (implClass == null) {
          // TODO: Add "Create Class" QuickFix (should be able to choose which module to create class in)
          addProblem(impl,
                  DevKitBundle.message("inspections.registration.problems.cannot.resolve.class",
                          DevKitBundle.message("class.implementation")),
                  ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        } else {
          final PsiClass componentClass = myPsiManager.findClass(type.myClassName, myScope);
          if (componentClass != null && !implClass.isInheritor(componentClass, true)) {
            addProblem(impl,
                    DevKitBundle.message("inspections.registration.problems.component.should.implement", type.myClassName),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    ImplementOrExtendFix.createFix(componentClass, implClass, myOnTheFly));
          }
          if (isAbstract(implClass)) {
            addProblem(impl,
                    DevKitBundle.message("inspections.registration.problems.abstract"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        }

        if (intf != null) {
          final PsiClass intfClass = myPsiManager.findClass(intf.getTrimmedText(), myScope);
          if (intfClass == null) {
            // TODO: Add "Create Class/Interface" QuickFix (should be able to choose which module to create class in)
            addProblem(intf,
                    DevKitBundle.message("inspections.registration.problems.cannot.resolve.class",
                            DevKitBundle.message("class.interface")),
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
          } else if (implClass != null) {
            if (myInterfaceClasses.contains(intfClass)) {
              addProblem(intf,
                      DevKitBundle.message("inspections.registration.problems.component.duplicate.interface", intfClass.getQualifiedName()),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
            if (intfClass != implClass && !implClass.isInheritor(intfClass, true)) {
              addProblem(impl,
                      DevKitBundle.message("inspections.registration.problems.component.incompatible.interface", intfClass.getQualifiedName()),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
            myInterfaceClasses.add(intfClass);
          }
        }
      }
      return true;
    }

    public boolean process(ActionType type, XmlTag action) {
      final XmlAttribute attribute = action.getAttribute("class", null);
      if (attribute != null) {
        final PsiElement token = getAttValueToken(attribute);
        if (token != null) {
          final PsiClass actionClass = myPsiManager.findClass(attribute.getValue().trim(), myScope);
          if (actionClass == null) {
            // TODO: Add "Create Class" QuickFix (should be able to choose which module to create class in)
            addProblem(token,
                    DevKitBundle.message("inspections.registration.problems.cannot.resolve.class",
                            DevKitBundle.message("class.action")),
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
          } else {
            if (!type.isOfType(actionClass)) {
              final PsiClass psiClass = myPsiManager.findClass(type.myClassName, myScope);
              if (psiClass != null && !actionClass.isInheritor(psiClass, true)) {
                addProblem(token,
                        DevKitBundle.message("inspections.registration.problems.action.incompatible.class", type.myClassName),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        ImplementOrExtendFix.createFix(psiClass, actionClass, myOnTheFly));
              }
            }
            if (!isPublic(actionClass)) {
              addProblem(token,
                      DevKitBundle.message("inspections.registration.problems.not.public"),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                      new MakePublicFix(actionClass, myOnTheFly));
            }
            final ConstructorType noArgCtor = ConstructorType.getNoArgCtor(actionClass);
            if (noArgCtor == null) {
              addProblem(token,
                      DevKitBundle.message("inspections.registration.problems.missing.noarg.ctor"),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                      new CreateConstructorFix(actionClass, myOnTheFly));
            } else if (noArgCtor != ConstructorType.DEFAULT && !isPublic(noArgCtor.myCtor)) {
              addProblem(token,
                      DevKitBundle.message("inspections.registration.problems.ctor.not.public"),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                      new MakePublicFix(noArgCtor.myCtor, myOnTheFly));
            }
            if (isAbstract(actionClass)) {
              addProblem(token,
                      DevKitBundle.message("inspections.registration.problems.abstract"),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
          }
        }
      }
      return true;
    }

    private void addProblem(XmlTagValue impl, String problem, ProblemHighlightType type, LocalQuickFix... fixes) {
      final XmlText[] textElements = impl.getTextElements();
      for (XmlText text : textElements) {
        if (text.getValue().trim().length() > 0) {
          addProblem(text, problem, type, fixes);
        }
      }
    }

    private void addProblem(PsiElement element, String problem, ProblemHighlightType type, LocalQuickFix... fixes) {
      if (myList == null) myList = new SmartList<ProblemDescriptor>();
      myList.add(myManager.createProblemDescriptor(element, problem, fixes, type));
    }

    @Nullable
    public ProblemDescriptor[] getProblems() {
      return myList != null ? myList.toArray(new ProblemDescriptor[myList.size()]) : null;
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
          if (ctor.getParameterList().getParametersCount() == 0) {
            return new ConstructorType(ctor);
          }
        }
        return null;
      }
      return ConstructorType.DEFAULT;
    }
  }
}
