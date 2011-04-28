/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.intentions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.codeInsight.template.macro.VariableOfTypeMacro;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.openapi.ui.Messages.showInputDialog;
import static org.jetbrains.android.util.AndroidUtils.VIEW_CLASS_NAME;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Mar 9, 2009
 * Time: 5:02:31 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidAddStringResourceAction extends AbstractIntentionAction {
  private static final String CONTEXT = AndroidUtils.ANDROID_PACKAGE + ".content.Context";
  private static final String RESOURCES = AndroidUtils.ANDROID_PACKAGE + ".content.res.Resources";

  @NotNull
  public String getText() {
    return AndroidBundle.message("add.string.resource.intention.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return AndroidBundle.message("intention.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return false;
    }
    PsiElement element = getPsiElement(file, editor);
    return element != null && getStringLiteralValue(element, file) != null;
  }

  @Nullable
  private static String getStringLiteralValue(@NotNull PsiElement element, @NotNull PsiFile file) {
    if (file instanceof PsiJavaFile && element instanceof PsiLiteralExpression) {
      PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      Object value = literalExpression.getValue();
      if (value instanceof String) {
        return (String)value;
      }
    }
    return null;
  }

  @Nullable
  private static PsiClass getContainingInheritorOf(@NotNull PsiElement element, @NotNull String... baseClassNames) {
    PsiClass c = null;
    do {
      c = PsiTreeUtil.getParentOfType(c == null ? element : c, PsiClass.class);
      for (String name : baseClassNames) {
        if (InheritanceUtil.isInheritor(c, name)) {
          return c;
        }
      }
    }
    while (c != null);
    return null;
  }

  @Nullable
  private static PsiElement getPsiElement(PsiFile file, Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    return element != null ? element.getParent() : null;
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    doInvoke(project, editor, file, null);
  }

  static void doInvoke(Project project, Editor editor, PsiFile file, @Nullable String resName) {
    AndroidFacet facet = AndroidFacet.getInstance(file);
    assert facet != null;
    PsiElement element = getPsiElement(file, editor);
    assert element != null;
    String value = getStringLiteralValue(element, file);
    assert value != null;
    value = value.replace("'", "\\'").replace("\"", "\\\"");
    String aPackage = getPackage(facet);
    if (aPackage == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("package.not.found.error"), CommonBundle.getErrorTitle());
      return;
    }
    if (resName == null) {
      resName =
        showInputDialog(project, AndroidBundle.message("resource.name"), AndroidBundle.message("add.string.resource.intention.text"),
                        Messages.getQuestionIcon(), "", new InputValidatorEx() {
            public String getErrorText(String inputString) {
              if (inputString == null || inputString.length() == 0) return null;
              String[] ids = inputString.split(".");
              for (String id : ids) {
                if (!StringUtil.isJavaIdentifier(id)) {
                  return AndroidBundle.message("android.identifier.expected", id);
                }
              }
              return null;
            }

            public boolean checkInput(String inputString) {
              return inputString != null && AndroidResourceUtil.isCorrectAndroidResourceName(inputString);
            }

            public boolean canClose(String inputString) {
              return checkInput(inputString);
            }
          });
    }
    if (resName == null) return;
    LocalResourceManager manager = facet.getLocalResourceManager();
    String resType = "string";
    ResourceElement resElement = manager.addValueResource(resType, resName, value);
    assert resElement != null;
    createJavaResourceReference(project, editor, file, element, aPackage, resName, resType);
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    UndoUtil.markPsiFileForUndo(file);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().saveAll();
      }
    });
  }

  private static void createJavaResourceReference(final Project project,
                                                  final Editor editor,
                                                  final PsiFile file,
                                                  final PsiElement element,
                                                  String aPackage,
                                                  String resName,
                                                  String resType) {
    final boolean extendsContext = getContainingInheritorOf(element, CONTEXT) != null;
    final String field = aPackage + ".R." + resType + '.' + AndroidResourceUtil.getRJavaFieldName(resName);
    final String methodName = getGetterNameForResourceType(resType);
    assert methodName != null;
    final TemplateImpl template;
    final boolean inStaticContext = RefactoringUtil.isInStaticContext(element, null);
    if (extendsContext && !inStaticContext) {
      template = new TemplateImpl("", "$resources$." + methodName + "(" + field + ")", "");
      MacroCallNode node = new MacroCallNode(new MyVarOfTypeExpression("getResources()"));
      node.addParameter(new ConstantNode(RESOURCES));
      template.addVariable("resources", node, new ConstantNode(""), true);
    }
    else {
      template = new TemplateImpl("", "$context$.getResources()." + methodName + "(" + field + ")", "");
      final boolean extendsView = getContainingInheritorOf(element, VIEW_CLASS_NAME) != null;
      MacroCallNode node =
        new MacroCallNode(extendsView && !inStaticContext ? new MyVarOfTypeExpression("getContext()") : new VariableOfTypeMacro());
      node.addParameter(new ConstantNode(CONTEXT));
      template.addVariable("context", node, new ConstantNode(""), true);
    }
    final int offset = element.getTextOffset();
    editor.getCaretModel().moveToOffset(offset);
    final TextRange elementRange = element.getTextRange();
    editor.getDocument().deleteString(elementRange.getStartOffset(), elementRange.getEndOffset());
    final RangeMarker marker = editor.getDocument().createRangeMarker(offset, offset);
    marker.setGreedyToLeft(true);
    marker.setGreedyToRight(true);
    TemplateManager.getInstance(project).startTemplate(editor, template, false, null, new TemplateEditingAdapter() {
      @Override
      public void waitingForInput(Template template) {
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(file, marker.getStartOffset(), marker.getEndOffset());
      }

      @Override
      public void beforeTemplateFinished(TemplateState state, Template template) {
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(file, marker.getStartOffset(), marker.getEndOffset());
      }
    });
  }

  @Nullable
  private static String getPackage(@NotNull AndroidFacet facet) {
    Manifest manifest = facet.getManifest();
    if (manifest == null) return null;
    return manifest.getPackage().getValue();
  }

  @Nullable
  private static String getGetterNameForResourceType(@NotNull String type) {
    if (type.length() < 2) return null;
    if (type.equals("dimen")) {
      return "getDimension";
    }
    return "get" + Character.toUpperCase(type.charAt(0)) + type.substring(1);
  }

  public boolean startInWriteAction() {
    return true;
  }

  private static class MyVarOfTypeExpression extends VariableOfTypeMacro {
    private final String myDefaultValue;

    private MyVarOfTypeExpression(@NotNull String defaultValue) {
      myDefaultValue = defaultValue;
    }

    @Override
    public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
      return new TextResult(myDefaultValue);
    }

    @Override
    public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
      return new TextResult(myDefaultValue);
    }

    @Override
    public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
      final PsiElement[] vars = getVariables(params, context);
      if (vars == null || vars.length == 0) {
        return null;
      }
      final Set<LookupElement> set = new LinkedHashSet<LookupElement>();
      for (PsiElement var : vars) {
        JavaTemplateUtil.addElementLookupItem(set, var);
      }
      LookupElement[] elements = set.toArray(new LookupElement[set.size()]);
      if (elements == null || elements.length == 0) {
        return elements;
      }
      LookupElement lookupElementForDefValue = LookupElementBuilder.create(myDefaultValue);
      LookupElement[] result = new LookupElement[elements.length + 1];
      result[0] = lookupElementForDefValue;
      System.arraycopy(elements, 0, result, 1, elements.length);
      return result;
    }
  }
}
